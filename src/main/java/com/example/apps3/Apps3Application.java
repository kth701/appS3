package com.example.apps3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lombok.extern.log4j.Log4j2;

/**
 * 애플리케이션의 메인 진입점 클래스
 * Spring Boot 애플리케이션을 초기화하고 실행
 * 
 * EC2에서 실행시 주의
 * -> 포워딩 설정했을 경우: http://내-EC2-IP:80으로 접속 -> http://내-EC2-IP:8080으로 리디렉션
 * 요청시 주의
 * : 포트는 생략 할 것 -> 리디렉션된 주소로 접속해야함( 80포트 또는 생략하면 -> 8080포트 전환됨)
 * ex)
 * http://ec2-13-125-216-251.ap-northeast-2.compute.amazonaws.com/api/sample/upload
 * 
 * 환경변수 설정시 주의:
 * : 환경변수 설정한 터미널에서만 유효함 -> java 실행시 터미널과 같아야함.
 * : 다른 터미널에서 실행시 환경변수 설정 필요
 * 
 * linux 환경변수에 있는 키를 인식되지 않을 경우 직접 입력하여 테스트 -> 정성 처리됨
 * ex) java -Dspring.cloud.aws.credentials.access-key=키값
 * -Dspring.cloud.aws.credentials.secret-key=시크릿키값 -jar
 * apps3-0.0.1-SNAPSHOT.jar(실행할 jar파일)
 *
 */
@SpringBootApplication
@Log4j2
public class Apps3Application {

	public static void main(String[] args) {
		SpringApplication.run(Apps3Application.class, args);
		log.info("=======================================");
		log.info("Applications started successfully!");
		log.info("=======================================");
	}

}
