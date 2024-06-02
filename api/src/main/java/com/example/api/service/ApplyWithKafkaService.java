package com.example.api.service;

import com.example.api.producer.CouponCreateProducer;
import com.example.api.repository.CouponCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplyWithKafkaService {
  private final CouponCountRepository couponCountRepository;
  private final CouponCreateProducer couponCreateProducer;

  public void apply(Long userId) {
    Long count = couponCountRepository.increment();

    if(count > 100){
      return;
    }
    couponCreateProducer.create(userId);
  }
}
