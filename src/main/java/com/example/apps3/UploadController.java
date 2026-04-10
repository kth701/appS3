package com.example.apps3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.apps3.dto.MultiPartFileDTO;
import com.example.apps3.util.LocalUploader;
import com.example.apps3.util.S3Uploader;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * 클라이언트의 파일 업로드 요청을 처리하는 REST 컨트롤러
 * 파일을 로컬에 임시로 저장하고 썸네일을 생성한 뒤, 이를 AWS S3로 전송하는 로직을 조율
 */
@RestController
@Log4j2
@RequiredArgsConstructor
@RequestMapping("/api/sample")
public class UploadController {

    /** 로컬 저장 및 썸네일 생성을 담당하는 유틸리티 */
    private final LocalUploader localUploader;
    /** AWS S3로의 파일 전송 및 삭제를 담당하는 유틸리티 */
    private final S3Uploader s3Uploader;

    /**
     * 서버 연결 상태를 확인하기 위한 테스트 엔드포인트
     */
    @org.springframework.web.bind.annotation.GetMapping("/upload")
    public String testConnection() {
        return "Upload API is running. Use POST with multipart/form-data to upload files.";
    }

    /**
     * 다중 파일 업로드를 처리합니다.
     * 1. 파일을 로컬에 저장 (이미지의 경우 썸네일 포함)
     * 2. 로컬에 저장된 파일들을 순차적으로 S3에 업로드
     * 3. S3 업로드 성공 시 로컬의 임시 파일은 삭제
     * 
     * @param multiPartFileDTO 업로드할 파일 객체 배열을 담은 DTO
     * @return S3에 최종 업로드된 파일들의 URL 리스트
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<String> upload(@ModelAttribute MultiPartFileDTO multiPartFileDTO) {
        if (multiPartFileDTO == null || multiPartFileDTO.getFiles() == null
                || multiPartFileDTO.getFiles().length == 0) {
            log.warn("업로드된 파일 데이터가 없거나 'files' 필드가 비어있습니다.");
            return List.of("No files found in the request. Please use 'files' as the field name.");
        }

        log.info("파일 업로드 프로세스 시작 (개수: {})", multiPartFileDTO.getFiles().length);

        return Arrays.stream(multiPartFileDTO.getFiles())
                .filter(file -> file != null && !file.isEmpty())
                .flatMap(file -> {
                    try {
                        return localUploader.uploadLocal(file).stream();
                    } catch (Exception e) {
                        log.error("로컬 업로드 중 오류 발생: {}", e.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                }) // 1. 로컬 저장 (원본 + 썸네일)
                .map(this::processS3UploadAndDeleteLocal) // 2. S3 업로드 및 로컬 삭제
                .collect(Collectors.toList());
    }

    /**
     * S3에 파일을 업로드하고 업로드에 성공한 로컬 파일을 삭제
     * 
     * @param localFilePath 로컬 파일의 경로
     * @return S3 업로드 URL
     */
    private String processS3UploadAndDeleteLocal(String localFilePath) {
        try {
            // S3 업로드
            String s3Url = s3Uploader.upload(localFilePath);
            log.info("S3 업로드 완료: {}", s3Url);

            // 로컬 임시 파일 삭제
            Path path = Paths.get(localFilePath);
            if (Files.deleteIfExists(path)) {
                log.info("로컬 임시 파일 삭제 성공: {}", localFilePath);
            }

            return s3Url;
        } catch (Exception e) {
            log.error("S3 업로드 또는 로컬 삭제 중 오류 발생 (경로: {}): {}", localFilePath, e.getMessage());
            throw new RuntimeException("파일 업로드 후처리 실패", e);
        }
    }
}
