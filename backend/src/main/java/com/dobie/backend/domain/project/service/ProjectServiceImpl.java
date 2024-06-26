package com.dobie.backend.domain.project.service;

import com.dobie.backend.domain.docker.dockercompose.service.DockerComposeService;
import com.dobie.backend.domain.docker.dockerfile.service.DockerfileService;
import com.dobie.backend.domain.nginx.service.NginxConfigService;
import com.dobie.backend.domain.project.dto.*;
import com.dobie.backend.domain.project.dto.file.FileGetDto;
import com.dobie.backend.domain.project.dto.file.FilePostDto;
import com.dobie.backend.domain.project.dto.file.FilePutDto;
import com.dobie.backend.domain.project.entity.Backend;
import com.dobie.backend.domain.project.entity.Database;
import com.dobie.backend.domain.project.entity.Frontend;
import com.dobie.backend.domain.project.entity.Project;
import com.dobie.backend.domain.project.entity.SettingFile;
import com.dobie.backend.domain.project.repository.ProjectRepository;
import com.dobie.backend.exception.exception.build.*;
import com.dobie.backend.exception.exception.file.SaveFileFailedException;
import com.dobie.backend.exception.exception.git.GitInfoNotFoundException;
import com.dobie.backend.util.command.CommandService;
import com.dobie.backend.util.file.FileManager;

import java.io.File;
import java.io.InputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Log4j2
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final CommandService commandService;
    private final DockerfileService dockerfileService;
    private final DockerComposeService dockerComposeService;
    private final NginxConfigService nginxConfigService;
    private final FileManager fileManager = new FileManager();

    @Override
    public String createProject(ProjectRequestDto dto) {
        String projectId = UUID.randomUUID().toString();
        Project project = new Project(projectId, dto);
        Map<String, SettingFile> fileMap = new HashMap<>();
        project.updateFileMap(fileMap);
        projectRepository.upsertProject(project);
        return projectId;
    }

    @Override
    public Map<String, ProjectGetResponseDto> getAllProjects() {
        Map<String, Project> map = projectRepository.selectProjects();
        Map<String, ProjectGetResponseDto> resultMap = new HashMap<>();
        map.forEach((key, value) -> {
            resultMap.put(key, new ProjectGetResponseDto(value));
        });
        return resultMap;
    }

    @Override
    public ProjectGetResponseDto getProject(String projectId) {
        Project project = projectRepository.searchProject(projectId);
        return new ProjectGetResponseDto(project);
    }

    @Override
    public List<BackendGetResponseDto> getAllBackends(String projectId) {
        Map<String, Backend> backendMap = projectRepository.selectBackends(projectId);
        List<BackendGetResponseDto> list = new ArrayList<>();
        backendMap.forEach((key, value) -> {
            list.add(new BackendGetResponseDto(value));
        });
        return list;
    }

    @Override
    public BackendGetResponseDto getBackend(String projectId, String serviceId) {
        Backend backend = projectRepository.searchBackend(projectId, serviceId);
        return new BackendGetResponseDto(backend);
    }

    @Override
    public FrontendGetResponseDto getFrontend(String projectId) {
        Frontend frontend = projectRepository.searchFrontend(projectId);
        return new FrontendGetResponseDto(frontend);
    }

    @Override
    public DatabaseGetResponseDto getDatabase(String projectId, String databaseId) {
        Database database = projectRepository.searchDatabase(projectId, databaseId);
        return new DatabaseGetResponseDto(database);
    }

    @Override
    public Map<String, DatabaseGetResponseDto> getAllDatabases(String projectId) {
        Map<String, Database> databaseMap = projectRepository.selectDatabases(projectId);
        Map<String, DatabaseGetResponseDto> dtoMap = new HashMap<>();
        databaseMap.forEach((key, value) -> {
            dtoMap.put(key, new DatabaseGetResponseDto(value));
        });
        return dtoMap;
    }

    @Override
    public Map<String, SettingFile> getAllFiles(String projectId) {
        return projectRepository.selectFiles(projectId);
    }

    @Override
    public void updateProject(ProjectRequestDto dto) {
        if (dto.getProjectId() == null) {
            dto.setProjectId(UUID.randomUUID().toString());
        }
        Project project = new Project(dto);
        Map<String, SettingFile> fileMap = getAllFiles(dto.getProjectId());
        project.updateFileMap(fileMap);
        projectRepository.upsertProject(project);
    }

    @Override
    public void deleteProject(String projectId) {
        Project project = projectRepository.searchProject(projectId);
        File directory = new File(project.getProjectName());

        // 디렉토리 존재 여부 확인
        if (directory.exists()) {
            // 프로젝트 중지 (이미지까지 삭제)
            String path = "./" + project.getProjectName();
            commandService.dockerComposeDown(path);
            System.out.println("프로젝트 삭제 시 컴포즈 다운 성공");
            // git clone한 디렉토리 삭제
            commandService.deleteDirectory(project.getProjectName());
            System.out.println("프로젝트 삭제 시 디렉토리 삭제 성공");
        }

        // Json에서 삭제
        projectRepository.deleteProject(projectId);
        // nginx config 파일 삭제
        commandService.deleteNginxProxyConf(projectId);
    }


    /*
     * 프로젝트 실행 관련 메서드들
     * */


    // 전체 프로젝트(main 브랜치에서 한번에 관리) 빌드 메서드
    // 사실상 dockerfile이랑 compose file 넣어놓는 용도
    @Override
    public void buildTotalService(String projectId) {
        ProjectGetResponseDto projectGetResponseDto = getProject(projectId);

        // git clone
        GitGetResponseDto gitInfo = projectGetResponseDto.getGit();

        String path = "./" + projectGetResponseDto.getProjectName();

        // 이미 clone 되어있는지 check
        if (!commandService.checkIsCloned(path)) {
            commandService.gitClone(gitInfo.getGitUrl(), gitInfo.getAccessToken());
        } else {
            commandService.gitPull(path);
        }


        // dockerfile 생성
        // 백엔드
        Map<String, BackendGetResponseDto> backendInfo = projectGetResponseDto.getBackendMap();
        backendInfo.forEach((key, value) -> {
            if (value.getFramework().equals("SpringBoot(gradle)")) {
                dockerfileService.createGradleDockerfile(projectGetResponseDto.getProjectName(), value.getVersion(), value.getPath());
            } else if (value.getFramework().equals("SpringBoot(maven)")) {
                dockerfileService.createMavenDockerfile(projectGetResponseDto.getProjectName(), value.getVersion(), value.getPath());
            } else if (value.getFramework().equals("Fastapi")) {
                dockerfileService.createFastApiDockerfile(projectGetResponseDto.getProjectName(), value.getVersion(), value.getPath());
            } else if (value.getFramework().equals("Django")) {
                dockerfileService.createDjangoDockerfile(projectGetResponseDto.getProjectName(), value.getVersion(), value.getPath(), value.getInternalPort());
            }
        });


        // 프론트엔드
        FrontendGetResponseDto frontendInfo = projectGetResponseDto.getFrontend();
        if (frontendInfo.getFramework().equals("React")) {
            dockerfileService.createReactDockerfile(projectGetResponseDto.getProjectName(), frontendInfo.getVersion(), frontendInfo.getPath(), projectGetResponseDto.getFrontend().isUsingNginx());
        } else if (frontendInfo.getFramework().equals("Vue")) {
            dockerfileService.createVueDockerfile(projectGetResponseDto.getProjectName(), frontendInfo.getVersion(), frontendInfo.getPath(), frontendInfo.getInternalPort());
        }

        // docker-compose 파일 생성
        dockerComposeService.createDockerComposeFile(projectGetResponseDto);

        //nginx proxy config 파일생성
        nginxConfigService.saveProxyNginxConfig(projectId, frontendInfo.isUsingNginx(), frontendInfo.getServiceId());

        if (frontendInfo.isUsingNginx()) {
            try {
                //frontend nginx config 파일 저장
                nginxConfigService.saveFrontNginxConfigFile(projectGetResponseDto.getFrontend().getPath(), projectGetResponseDto.getProjectName());
            } catch (IOException e) {
                log.error(e.getMessage());
                throw new SaveFileFailedException("front nginx config 파일 저장에 실패했습니다."); //예외처리
            }
        }
    }

    @Override
    public void stopProject(String projectId) {
        ProjectGetResponseDto projectGetResponseDto = getProject(projectId);
        String path = "./" + projectGetResponseDto.getProjectName();
        // nginx config 파일 삭제
        commandService.deleteNginxProxyConf(projectId);
        commandService.dockerComposeDown(path);
    }

    // 프로젝트 통째로 실행한다 했을때
    @Override
    public void runProject(String projectId) {
        String filePath = "/nginx/" + projectId + ".conf";
        if (!new File(filePath).exists()) {
            throw new NginxConfigNotFoundException();
        } else {
            ProjectGetResponseDto projectGetResponseDto = getProject(projectId);
            String path = "./" + projectGetResponseDto.getProjectName();
            commandService.dockerComposeUp(path);

            if (!verifyComposeUpSuccess(path)) {
                throw new ProjectStartFailedException("Verify compose up failed.");
            }
            commandService.restartNginx();
        }
    }

    @Override
    public boolean verifyComposeUpSuccess(String path) {
        try {
            String command = "docker compose -f " + path + "/docker-compose.yml ps";
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("Up")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new ProjectStartFailedException(e.getMessage(), "컨테이너 실행 검증에 실패했습니다.");
        }
    }

    @Override
    public void rebuildAndStartProject(String projectId) {
        ProjectGetResponseDto dto = getProject(projectId);
        GitGetResponseDto gitInfo = dto.getGit();
        String path = "./" + dto.getProjectName();

        try {

            // git pull
            if (commandService.checkIsCloned(path)) {
                commandService.gitPull(path);
            } else {
                log.info("프로젝트 정보가 없습니다. Build를 처음부터 진행합니다.");
                buildTotalService(projectId);
            }
        } catch (Exception e) {
            e.getStackTrace();
        }

        // projectRestart
        commandService.dockerComposeUp(path);
        if (!verifyComposeUpSuccess(path)) {
            throw new ProjectStartFailedException("Verify compose up failed.");
        }
    }

    @Override
    public void addFile(FilePostDto dto, List<MultipartFile> files) {

        Map<String, SettingFile> fileMap = new HashMap<>();

        // 파일 저장
        for (int i = 0; i < dto.getFileList().size(); i++) {

            String uuid = UUID.randomUUID().toString();
            fileMap.put(uuid, new SettingFile(uuid, dto.getFileList().get(i).getFilePath(), dto.getFileList().get(i).getFileName()));

            // 이미 저장되어 있는 파일은 파일명이 placeholder로 시작해서 조사해서 건너뛰기
            if (files.get(i).getOriginalFilename().startsWith("placeholder-")) {
                // 이 파일은 플레이스홀더
                System.out.println(i + "번 파일은 플레이스홀더입니다.");
                continue;
            }

            // 파일 StringBuilder 에 넣고 저장하기
            StringBuilder sb = new StringBuilder();
            try (InputStream inputStream = files.get(i).getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }

            } catch (IOException e) {
            }
            String ignoreFile = sb.toString();

            // ec2 서버에서 깃클론하는 경로로 수정하기
            String filePath = "./" + dto.getProjectName() + dto.getFileList().get(i).getFilePath();
            fileManager.createFolder(filePath, files.get(i).getOriginalFilename(), ignoreFile);
        }

        // project.json 파일 수정
        Project project = projectRepository.searchProject(dto.getProjectId());
        project.updateFileMap(fileMap);
        projectRepository.upsertProject(project);

    }

    @Override
    public List<FileGetDto> getFile(String projectId) {
        Map<String, SettingFile> fileMap = getAllFiles(projectId);
        List<FileGetDto> result = new ArrayList<>();
        fileMap.forEach((key, value) -> {
            result.add(new FileGetDto((value)));
        });
        return result;
    }

    @Override
    public void deleteFile(FilePutDto dto) {

        // 파일 삭제
        String filePath = "./" + dto.getProjectName() + dto.getFilePath();
        fileManager.deleteFile(filePath, dto.getFileName());

        // prject.json 파일 수정
        Map<String, SettingFile> fileMap = getAllFiles(dto.getProjectId());
        fileMap.remove(dto.getFileId());

        Project project = projectRepository.searchProject(dto.getProjectId());
        project.updateFileMap(fileMap);
        projectRepository.upsertProject(project);


    }

    @Override
    public void stopService(String containerName) {
        commandService.dockerStop(containerName);
    }

    @Override
    public void startService(String containerName) {
        commandService.dockerStart(containerName);
    }


}

