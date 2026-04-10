package com.example.apps3.util;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3 버킷에 파일을 업로드하고 관리하는 유틸리티 클래스
 * Spring Cloud AWS의 S3Template과 AWS SDK의 S3Client를 사용하여 S3 작업을 처리
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class S3Uploader {

    /** Spring Cloud AWS에서 제공하는 S3 작업 템플릿 */
    private final S3Template s3Template;

    /** AWS SDK S3 client (추가적인 설정이나 하위 레벨 작업 시 사용) */
    private final S3Client s3Client;

    /** application.yml/properties에 설정된 S3 버킷 이름 */
    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * 로컬 파일 경로를 받아 S3에 업로드
     * 
     * @param filePath 업로드할 로컬 파일의 절대 경로
     * @return S3에 업로드된 객체의 공개 URL
     * @throws IllegalArgumentException 파일이 존재하지 않거나 유효하지 않은 경우 발생
     */
    public String upload(String filePath) {
        File targetFile = new File(filePath);
        if (!targetFile.exists() || !targetFile.isFile()) {
            throw new IllegalArgumentException("파일이 존재하지 않습니다: " + filePath);
        }
        return upload(targetFile);
    }

    /**
     * File 객체를 받아 S3에 업로드. 로컬 원본 파일은 유지
     * 
     * @param uploadFile 업로드할 File 객체
     * @return S3에 업로드된 객체의 공개 URL
     */
    public String upload(File uploadFile) {
        if (uploadFile == null || !uploadFile.exists() || !uploadFile.isFile()) {
            throw new IllegalArgumentException("유효한 파일을 넣어주세요.");
        }
        return upload(uploadFile, uploadFile.getName(), false);
    }

    /**
     * 바이트 배열 데이터를 받아 S3에 업로드
     * 내부적으로 임시 파일을 생성하여 업로드한 후, 업로드가 완료되면 임시 파일을 자동으로 삭제
     * 
     * @param fileBytes 파일 데이터 (byte array)
     * @param fileName  S3에 저장될 파일 이름
     * @return S3에 업로드된 객체의 공개 URL
     */
    public String upload(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileBytes와 fileName은 null이거나 빈 값일 수 없습니다.");
        }

        Path tempPath = null;
        try {
            // S3 전송을 위해 임시 파일 생성
            tempPath = Files.createTempFile("s3upload-", "");
            Files.write(tempPath, fileBytes);
            // 업로드 후 로컬 임시 파일 삭제 옵션(true)으로 호출
            return upload(tempPath.toFile(), fileName, true);
        } catch (Exception e) {
            log.error("byte[] 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("S3 업로드 실패", e);
        } finally {
            // 임시 파일이 남아있다면 제거
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ex) {
                    log.warn("임시 파일 삭제 실패: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * 실제 S3 업로드 작업을 수행하고, 설정에 따라 로컬 파일을 삭제
     * 
     * @param uploadFile        업로드할 File 객체
     * @param fileName          S3에 저장될 키(Key) 이름
     * @param removeAfterUpload 업로드 성공 후 로컬 파일을 삭제할지 여부
     * @return S3에 업로드된 객체의 공개 URL
     */
    private String upload(File uploadFile, String fileName, boolean removeAfterUpload) {
        try {
            // S3에 데이터 저장 수행
            String url = putS3(uploadFile, fileName);

            // 후처리: 로컬 파일 삭제가 필요한 경우 수행
            if (removeAfterUpload && uploadFile.exists()) {
                removeOriginalFile(uploadFile);
            }
            return url;
        } catch (Exception e) {
            log.error("S3 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("S3 업로드 실패", e);
        }
    }

    /**
     * AWS SDK S3Client를 사용하여 실제 버킷에 파일을 전송
     * 
     * @param uploadFile 업로드할 물리 파일
     * @param fileName   S3 객체 키
     * @return 생성된 S3 객체의 접근 URL
     */
    private String putS3(File uploadFile, String fileName) {
        try (FileInputStream inputStream = new FileInputStream(uploadFile)) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    // 필요 시 여기에 Content-Type 등을 지정
                    .build();

            s3Client.putObject(
                    putObjectRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromInputStream(inputStream, uploadFile.length()));

            log.info("S3 업로드 성공: {}", fileName);
            // 업로드된 파일의 URL 형식 생성 (리전을 한국 리전으로 가정)
            return "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + fileName;
        } catch (Exception e) {
            log.error("S3 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("S3 업로드 실패", e);
        }
    }

    /**
     * 로컬 시스템의 파일을 삭제
     * 
     * @param targetFile 삭제 대상 파일
     */
    private void removeOriginalFile(File targetFile) {
        if (targetFile.exists() && targetFile.delete()) {
            log.info("로컬 파일 삭제 성공: {}", targetFile.getName());
        } else {
            log.warn("로컬 파일 삭제 실패: {}", targetFile.getName());
        }
    }

    /**
     * S3 버킷에 저장된 특정 파일을 삭제
     * 
     * @param fileName 삭제할 객체의 키(Key)
     */
    public void removeS3File(String fileName) {
        try {
            s3Client.deleteObject(builder -> builder.bucket(bucket).key(fileName));
            log.info("S3 파일 삭제 성공: {}", fileName);
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패: {}", e.getMessage());
            throw new RuntimeException("S3 파일 삭제 실패", e);
        }
    }
}
