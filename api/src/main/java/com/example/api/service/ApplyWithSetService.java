package com.example.api.service;

import com.example.api.producer.CouponCreateProducer;
import com.example.api.repository.AppliedUserRepository;
import com.example.api.repository.CouponCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplyWithSetService {
  private final CouponCountRepository couponCountRepository;
  private final CouponCreateProducer couponCreateProducer;
  private final AppliedUserRepository appliedUserRepository;

  public void apply(Long userId) {
    Long apply = appliedUserRepository.add(userId);

    if(apply != 1){
      return;
    }

    Long count = couponCountRepository.increment();

    if(count > 100){
      return;
    }

    couponCreateProducer.create(userId);
  }
}
