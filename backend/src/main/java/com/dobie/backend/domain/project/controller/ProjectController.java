package com.dobie.backend.domain.project.controller;

import com.dobie.backend.domain.project.dto.ProjectRequestDto;
import com.dobie.backend.domain.project.dto.ProjectGetResponseDto;
import com.dobie.backend.domain.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Project 컨트롤러", description = "Project Controller API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/project")
public class ProjectController {

    private final ProjectService  projectService;

    @Operation(summary = "전체 프로젝트", description = "전체 프로젝트")
    @GetMapping("")
    public ResponseEntity<?> getAllProjects(){
        Map<String, ProjectGetResponseDto> map = projectService.getAllProjects();
        return new ResponseEntity<Map<String, ProjectGetResponseDto>>(map, HttpStatus.OK);
    }

    @Operation(summary = "프로젝트 등록", description = "프로젝트 등록")
    @PostMapping("/create")
    public ResponseEntity<?> createProject(@RequestBody ProjectRequestDto dto){
        projectService.createProject(dto);

        return new ResponseEntity<ProjectRequestDto>(dto, HttpStatus.OK);
    }

    @Operation(summary = "프로젝트 수정", description = "프로젝트 수정")
    @PutMapping("/update/{projectId}")
    public ResponseEntity<?> updateProject(@PathVariable String projectId, @RequestBody ProjectRequestDto dto){
        projectService.updateProject(projectId, dto);

        return new ResponseEntity<ProjectRequestDto>(dto, HttpStatus.OK);
    }

    @Operation(summary = "프로젝트 삭제", description = "프로젝트 삭제")
    @DeleteMapping("/delete/{projectId}")
    public  ResponseEntity<?> deleteProject(@PathVariable String projectId){
        projectService.deleteProject(projectId);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "프로젝트 빌드", description = "등록된 프로젝트(백, 프론트, db) 정보를 바탕으로 build파일 생성")
    @PostMapping("/build/{projectId}")
    public ResponseEntity<?> buildTotalProject(@PathVariable String projectId){
        projectService.buildTotalService(projectId);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "프로젝트 실행", description = "dockerfile, compose 파일 바탕으로 프로젝트 빌드 후 실행")
    @PostMapping("/run/{projectId}")
    public ResponseEntity<?> runProject(@PathVariable String projectId){
        projectService.runProject(projectId);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}

