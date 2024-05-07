import axios from "axios";
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import styles from "./ProjectList.module.css";
import { getProject } from "../../api/Main";

import ProjectItem from "./ProjectItem";

export default function ProjectList() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState({});

  useEffect(() => {
    try {
      getProjectList(); //useEffect안에서 await처리 안돼서 함수로 빼서 실행
    } catch (error) {
      console.error("유저정보 조회 실패 에러: ", error);
    }
  }, []);

  const getProjectList = async (e) => {
    try {
      const response = await getProject();
      console.log(response.data.data);
      setProjects(response.data.data);
    } catch (error) {
      console.error("프로젝트 조회 실패:", error);
    }
  };

  return (
    <>
      <div className={styles.table} onClick={() => navigate("/manage")}>
        <div className={styles.colume}>
          <div>프로젝트명</div>
          <div>도메인주소</div>
          <div>실행</div>
          <div>Git Link</div>
        </div>
        <div className={styles.projectlist}>
          {Object.values(projects).map((project) => (
            <div key={project.projectId}>
              <ProjectItem project={project} />
            </div>
          ))}
        </div>
      </div>
    </>
  );
}