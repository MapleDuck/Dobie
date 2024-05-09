import { useState, useEffect, useRef } from "react";
import styles from "./LoadingModal.module.css";
import Animaion from "../common/Animaion";
import StopAnimaion from "../common/StopAnimaion";

export default function Madal({ action, setModalOpen }) {
    const modalBackground = useRef();

    return (
        <>
            <div
                className={styles.modalContainer}
                ref={modalBackground}
                onClick={(e) => {
                    if (e.target === modalBackground.current) {
                        setModalOpen(false);
                    }
                }}
            >
                {action === "run" && (
                    <div className={styles.modalContent}>
                        <Animaion />
                        <div className={styles.runtext}>Building...</div>
                        <div className={styles.modalhead}>
                        </div>
                    </div>
                )}
                {action === "stop" && (
                    <div className={styles.modalContent}>
                        <StopAnimaion />
                        <div className={styles.stoptext}>Stopping...</div>
                        <div className={styles.modalhead}>
                        </div>
                    </div>
                )}
            </div>
        </>
    );
}