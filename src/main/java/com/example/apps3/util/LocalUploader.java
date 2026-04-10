package com.example.apps3.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.log4j.Log4j2;
import net.coobird.thumbnailator.Thumbnails;

/**
 * 로컬 서버의 파일 시스템에 파일을 업로드하고 관리하는 유틸리티 클래스
 * 이미지 파일의 경우 Thumbnailator 라이브러리를 사용하여 썸네일을 자동으로 생성
 */
@Component
@Log4j2
public class LocalUploader {

    /**
     * application.properties 파일에서 설정한 업로드 기본 경로
     */
    @Value("${com.example.upload.path}")
    private String uploadPath;

    /**
     * 빈(Bean) 초기화 직후 실행되며, 설정된 업로드 경로에 디렉토리가 없으면 생성
     */
    @PostConstruct
    public void init() {
        File tempFolder = new File(uploadPath);
        if (!tempFolder.exists()) {
            tempFolder.mkdirs();
            log.info("업로드 디렉토리 생성됨: {}", uploadPath);
        }
    }

    /**
     * 클라이언트로부터 전송된 MultipartFile을 로컬 파일 시스템에 저장
     * 파일 형식이 이미지(image/*)인 경우, 200x200 크기의 썸네일을 추가로 생성
     *
     * @param multipartFile 업로드될 원본 파일
     * @return 저장된 모든 파일(원본 및 썸네일)의 절대 경로 리스트
     * @throws IllegalArgumentException 파일이 비어있거나 없는 경우 발생
     * @throws RuntimeException         파일 입출력 오류 시 발생
     */
    public List<String> uploadLocal(MultipartFile multipartFile) {
        // 파일 유효성 검사
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }

        // 동일한 파일명 업로드 시 덮어쓰기를 방지하기 위해 UUID를 접두어로 붙임
        // 예: 3f2a..._test.jpg 형식으로 저장됩니다.
        String saveFileName = UUID.randomUUID() + "_" + multipartFile.getOriginalFilename();
        
        // 설정된 uploadPath와 생성된 파일명을 결합하여 전체 저장 경로를 생성합니다.
        Path savePath = Paths.get(uploadPath, saveFileName);
        List<String> savePathList = new ArrayList<>();

        try {
            // 1. 원본 파일 저장: 클라이언트가 보낸 MultipartFile 데이터를 지정된 물리적 경로(savePath)로 복사하거나 이동
            // 내부적으로 파일 시스템의 효율적인 네이티브 메서드를 사용
            multipartFile.transferTo(savePath);
            
            // 저장된 파일의 시스템상 전체 위치를 나타내는 절대 경로(Absolute Path)를 문자열로 변환하여 추출
            String absolutePath = savePath.toAbsolutePath().toString();
            
            // 이후 S3 업로드 프로세스에서 이 파일을 참조할 수 있도록, 성공적으로 저장된 파일의 경로를 리스트에 추가
            savePathList.add(absolutePath);
            log.info("파일 저장 성공: {}", absolutePath);

            // 2. 이미지 파일인 경우 썸네일 생성 처리
            String contentType = multipartFile.getContentType();
            if (contentType != null && contentType.startsWith("image")) {
                generateThumbnail(savePath.toFile(), saveFileName, savePathList);
            }

        } catch (IOException e) {
            log.error("파일 저장 실패: {}", e.getMessage());
            throw new RuntimeException("로컬 파일 저장 실패", e);
        }

        return savePathList;
    }

    /**
     * 원본 이미지 파일을 바탕으로 썸네일을 생성
     * 접두어 's_'를 붙여 구분 가능하도록 저장
     *
     * @param originalFile 원본 이미지 파일 객체
     * @param fileName     저장 시 사용할 기본 파일명
     * @param savePathList 생성된 썸네일 경로를 추가할 리스트
     */
    private void generateThumbnail(File originalFile, String fileName, List<String> savePathList) {
        try {
            // 접두어 's_' (small)를 사용하여 썸네일 파일 경로 지정
            File thumbFile = new File(uploadPath, "s_" + fileName);

            // Thumbnailator를 사용한 리사이징 진행
            Thumbnails.of(originalFile)
                    .size(200, 200) // 최대 폭/높이 설정
                    .keepAspectRatio(true) // 비율 유지
                    .toFile(thumbFile);

            String thumbPath = thumbFile.getAbsolutePath();
            savePathList.add(thumbPath);
            log.info("썸네일 생성 성공: {}", thumbPath);
        } catch (IOException e) {
            log.warn("썸네일 생성 실패: {}", e.getMessage());
            // 썸네일 생성 실패는 비즈니스 로직상 치명적이지 않다고 판단하여 경고 로그만 남김
        }
    }
}
