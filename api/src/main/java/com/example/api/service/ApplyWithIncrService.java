package com.example.api.service;

import com.example.api.domain.Coupon;
import com.example.api.repository.CouponCountRepository;
import com.example.api.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplyWithIncrService {
  private final CouponRepository couponRepository;
  private final CouponCountRepository couponCountRepository;

  public void apply(Long userId) {
//    long count = couponRepository.count();
    Long count = couponCountRepository.increment();
    
    if(count > 100){
      return;
    }
    couponRepository.save(new Coupon(userId));
  }
}
