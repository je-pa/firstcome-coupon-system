# 선착순 할인 쿠폰 이벤트 시스템

## 요구사항 정의
선착순 100명에게 할인쿠폰을 제공하는 이벤트를 진행하고자 한다.

이 이벤트는 아래와 같은 조건을 만족하여야 한다.
- 선착순 100명에게만 지급되어야한다.
- 101개 이상이 지급되면 안된다.
- 순간적으로 몰리는 트래픽을 버틸 수 있어야합니다.

## 첫 시도
- [ApplyService 코드 바로가기](/api/src/main/java/com/example/api/service/ApplyService.java)
```java
@RequiredArgsConstructor
public class ApplyService {

  private final CouponRepository couponRepository;

  public void apply(Long userId) {
    long count = couponRepository.count();

    if(count > 100){
      return;
    }
    couponRepository.save(new Coupon(userId));
  }
}
```

### 문제점 알아보기
위의 코드에서의 문제점은 멀티 스레드 환경에서 나타난다.
- [ApiApplicationTests 코드 바로가기](/api/src/test/java/com/example/api/service/ApiApplicationTests.java)

-  문제점: 100개만 생성되는 것을 기대했지만 멀티 스레드 환경에서는 생각처럼 되지않음.
- 이유: 멀티 스레드 환경에서는 **레이스 컨디션**이 발생하기 때문에

> **레이스 컨디션이란?**
> 
>  두 개 이상의 쓰레드가 공유 데이터에 access을 하고 동시에 작업을 하려고 할 때 발생하는 문제
> 
> 예시)
>
>    | Thread1       | Thread2       | Coupon count |
>    |---------------|---------------|--------------|
>    | select count  |               | 99           |
>    |               | select count  | 99           |
>    | create coupon |               | 100          |
>    |               | create coupon | 101          |
> 
> 표에서 볼 수 있듯 Thread1이 쿠폰을 조회하고 생성을 하는 중 
> 다른 스레드 Thread2가 쿠폰을 조회해버리면 coupon count가 100을 초과하게 되는 것이다.

## Redis로 해결하기
- 문제점 분석
  - 레이스 컨디션이 일어나는 부분은 쿠폰 개수를 가져오는 부분이었다.
- 해결 방법 생각해보기
  1. 쿠폰 발급 로직 전체를 싱글 스레드로 작업하게 한다.
