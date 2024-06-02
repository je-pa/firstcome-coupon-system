package com.example.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.api.repository.CouponRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplyWithKafkaServiceTest {
  @Autowired
  private ApplyWithKafkaService applyWithKafkaService;

  @Autowired
  private CouponRepository couponRepository;

  @Test
  @DisplayName("한번만 응모")
  void applyOnlyOne() {
    applyWithKafkaService.apply(1L);

    long count = couponRepository.count();

    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("여러명 응모")
  void applyMany() throws InterruptedException {
    int threadCount = 1000;
    // 멀티스레드 구성
    // ExecutorService: 병렬 작업 도와주는 java api
    ExecutorService executorService = Executors.newFixedThreadPool(32);

    // 요청이 끝날 때까지 기다리도록 사용
    // CountDownLatch: 다른 Thread에서 수행하는 작업을 기다리도록 도와주는 클래스
    CountDownLatch latch = new CountDownLatch(threadCount);

    for(int i=0 ; i<threadCount ; i++){
      long userId = i;
      executorService.submit(() -> {
        try {
          applyWithKafkaService.apply(userId);
        } finally {
          latch.countDown();
        }
      });
    }
    latch.await();

    Thread.sleep(10000);

    long count = couponRepository.count();

    assertThat(count).isEqualTo(100);
  }
}