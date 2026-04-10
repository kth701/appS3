package com.example.apps3.util;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Log4j2
public class S3UploaderTest {

    @Autowired
    private S3Uploader s3Uploader;

    @Autowired
    private S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("실제 S3 파일 업로드 및 URL 반환 테스트")
    public void testUploadRealS3() throws Exception {
        // 1. 준비: 고유한 파일명 및 임시 로컬 파일 생성 (@TempDir 활용)
        String fileName = "test_" + UUID.randomUUID() + ".txt";
        Path testFile = tempDir.resolve(fileName); 
        Files.writeString(testFile, "S3 업로드 테스트 데이터: " + fileName);

        try {
            // 2. 실행: 업로드
            String url = s3Uploader.upload(testFile.toString());

            // 3. 검증
            assertNotNull(url, "업로드 후 반환된 URL은 null이 아니어야 합니다.");
            log.info("업로드 성공 - URL: {}", url);

            // S3에 실제로 객체가 존재하는지 확인
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();
            
            assertDoesNotThrow(() -> {
                var response = s3Client.headObject(headObjectRequest);
                log.info("S3 객체 존재 확인: {} bytes", response.contentLength());
            }, "S3에 파일이 존재해야 합니다.");

        } finally {
            // 4. 정리: S3 파일 삭제
            cleanupS3(fileName);
        }
    }

    @Test
    @DisplayName("S3 파일 삭제 기능 테스트")
    public void testRemoveS3File() throws Exception {
        // 1. 준비: 임시 파일 업로드
        String fileName = "delete_test_" + UUID.randomUUID() + ".txt";
        Path testFile = tempDir.resolve(fileName);
        Files.writeString(testFile, "삭제 테스트용 데이터"); 
        s3Uploader.upload(testFile.toString());

        // 2. 실행: 삭제
        s3Uploader.removeS3File(fileName);
        log.info("S3 파일 삭제 요청 완료: {}", fileName);

        // 3. 검증: assertThrows를 사용하여 특정 예외가 발생하는지 확인
        // 첫 번째 인자: 발생할 것으로 예상되는 예외 클래스 (NoSuchKeyException)
        // 두 번째 인자: 예외가 발생해야 하는 실행 코드 블록 (람다식)
        // 세 번째 인자: 테스트 실패 시 출력될 메시지
        // S3에서 파일이 정상적으로 삭제되었다면, headObject 호출 시 NoSuchKeyException이 발생해야 함
        assertThrows(NoSuchKeyException.class, () -> 
            s3Client.headObject(b -> b.bucket(bucket).key(fileName))
        , "S3에서 파일이 삭제되어 조회가 실패해야 합니다.");
    }

    @Test
    @DisplayName("byte 배열을 이용한 S3 업로드 테스트")
    public void testUploadBytes() throws Exception {
        // 1. 준비
        String fileName = "byte_test_" + UUID.randomUUID() + ".txt";
        byte[] content = "Hello S3 from Byte Array".getBytes();

        try {
            // 2. 실행
            String url = s3Uploader.upload(content, fileName);

            // 3. 검증
            assertNotNull(url); 
            assertDoesNotThrow(() -> 
                s3Client.headObject(b -> b.bucket(bucket).key(fileName))
            );
            log.info("Byte 배열 업로드 성공: {}", url);

        } finally {
            // 4. 정리
            cleanupS3(fileName);
        }
    }

    @Test
    private void cleanupS3(String fileName) {
        try {
            s3Uploader.removeS3File(fileName);
            log.info("테스트 정리 - S3 파일 삭제 완료: {}", fileName);
        } catch (Exception e) {
            log.warn("테스트 정리 실패 (S3 파일 삭제): {}", e.getMessage());
        }
    }
}


/*

주요 사항

   1. 이식성 및 독립성 강화 (@TempDir 도입):
       * 기존의 하드코딩된 로컬 경로(C:\\upload\\)를 제거하고, JUnit 5에서 제공하는 @TempDir을 사용하여 테스트 실행 시마다 독립적인 임시 디렉토리를 사용하도록 변경했습니다. 이를 통해 윈도우뿐만 아니라 리눅스/Mac
         환경에서도 테스트가 정상 작동합니다.
   2. 데이터 충돌 방지 (UUID 활용):
       * S3 버킷 내에서 파일명이 겹치지 않도록 UUID를 사용하여 고유한 파일명을 생성합니다. 여러 개발자가 동시에 테스트하거나 병렬로 실행될 때 발생할 수 있는 간섭을 방지했습니다.
   3. 테스트 커버리지 확대:
       * 기존에 누락되었던 byte[] 배열을 이용한 업로드(testUploadBytes) 테스트 케이스를 추가했습니다.
   4. 코드 안정성 및 가독성 개선:
       * S3Uploader.java에 포함되어 있던 문법 오류(오타 /)를 수정했습니다.
       * assertDoesNotThrow와 assertThrows(NoSuchKeyException.class, ...)를 사용하여 삭제 및 조회 성공 여부를 더 명확하고 간결하게 검증합니다.
       * S3 리소스 정리를 위해 finally 블록과 cleanupS3 헬퍼 메서드를 도입하여 테스트 실패 시에도 S3에 잔류 파일이 남지 않도록 보완했습니다.

  이제 ./gradlew test 명령어를 통해 리팩토링된 테스트 코드를 실행해 보실 수 있습니다. (AWS 환경 변수 설정이 필요할 수 있습니다.)


*/