# 선착순 할인 쿠폰 이벤트 시스템

## 요구사항 정의
선착순 100명에게 할인쿠폰을 제공하는 이벤트를 진행하고자 한다.

이 이벤트는 아래와 같은 조건을 만족하여야 한다.
- 선착순 100명에게만 지급되어야한다.
- 101개 이상이 지급되면 안된다.
- 순간적으로 몰리는 트래픽을 버틸 수 있어야한다.

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

## 문제점 분석하기
위의 코드에서의 문제점은 멀티 스레드 환경에서 나타난다
- [ApiApplicationTests 코드 바로가기](/api/src/test/java/com/example/api/service/ApplyServiceTest.java)
  - 해당 테스트 코드를 이용해 테스트를 하면 실패하는 것을 알 수 있다.

- 문제점: 100개만 생성되는 것을 기대했지만 멀티 스레드 환경에서는 생각처럼 되지않음
  - 이유: 멀티 스레드 환경에서는 **레이스 컨디션**이 발생하기 때문
- 레이스 컨디션이 일어나는 부분은 쿠폰 개수를 가져오는 부분이 문제인 것이다. 아래 예시를 통해 살펴보자

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

## 해결 방법 생각해보기
1. 쿠폰 발급 로직 전체를 싱글 스레드로 작업하게 한다.
   - 먼저 요청한 사람의 쿠폰이 발급된 이후에 다른 사람들의 쿠폰 발급이 가능해지기 때문에 성능이 좋지 않을 것이다.
2. Java의 Synchronized
   - 서버가 여러 대가 된다면 여전히 레이스 컨디션이 발생할 것이다.
3. MySQL, Redis를 활용한 락
   - 락을 활용한다면 발급된 쿠폰 개수를 가져오는 것부터 쿠폰을 생성할 때까지 락을 걸어야한다.
   - 이는 락을 거는 구간이 길어져서 성능에 불이익이 있을 수 있다.
4. Redis incr 명령어
   - 우리의 목적은 쿠폰 개수에 대한 정합성이다.(일정 개수까지만 쿠폰을 발급해야한다.)
   - 레디스는 싱글 스레드 기반으로 동작해서 레이스 컨디션을 해결할 수 있다.
   - 해당 명령어를 사용하여 발급된 쿠폰 개수를 제어하면 성능도 빠르고 정합성도 지킬 수 있을 것이다.

# Redis incr 간단 실습
> Redis incr은 키에 대한 밸류는 1씩 증가시키는 명령어다. 성능도 빠른 명령어다.
간단하게 실습을 해보자.
1. docker ps를 한 후 레디스의 컨테이너 아이디를 복사
  ```
    docker ps
  ```
2. redis-cli 실행
  ```
     docker exec -it {복사한 컨테이너 아이디} redis-cli
  ```
3. incr 사용해보기
  ```
    127.0.0.1:6379> incr coupon_count
    (integer) 1
    127.0.0.1:6379> incr coupon_count
    (integer) 2
    127.0.0.1:6379>
  ```


# incr로 해결하기
이제 incr 명령어를 활용해서 발급된 쿠폰의 개수를 제어해보자.

쿠폰을 발급하기 전에 쿠폰 카운트를 1증가 시키고 리턴되는 값이
100보다 크다면 이미 100개이상 발급되었다는 뜻이므로 쿠폰 발급이 더이상 되면 안되도록 로직을 작성해보자.

> 참고: flushall을 사용해서 위에서 실습했던 데이터를 초기화 시키고 진행하자
> ```
>   flushall
> ```

- 관련 코드
  - [ApplyWithIncrService](api/src/main/java/com/example/api/service/ApplyWithIncrService.java)
  - [ApplyWithIncrServiceTest](api/src/test/java/com/example/api/service/ApplyWithIncrServiceTest.java)

아래와 같이 redis incr를 사용한 로직으로 데이터 정합성을 지킬 수 있다.
```java
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
```

## incr를 활용한 로직의 부가 설명

| 시간   | Thread1           | Thread2              | Redis-count |
|-------|-------------------|----------------------|-------------|
| 10:00 | start - 10:00     | wait                 | 99          |
| 10:01 | incr coupon_count | wait                 | 99          |
| 10:02 | end - 10:02       | start -10:02         | 100         |
| 10:03 | create coupon     | end - 10:03          | 101         |
|       |                   | failed create coupon | 101         |

Redis는 싱글 스레드 기반으로 동작하기 때문에

Thread1에서 쿠폰 카운트를 증가시키는 명령어를 실행하고 10:02에 완료가 된다고 할 때

Thread2에서 10시 1분에 쿠폰 카운트를 증가 시키려 했을 때 Thread1의 종료를 기다리고 10:02에 시작하게 된다.

때문에 Thread에서는 언제나 최신 값을 가져갈 수 있게 되어 쿠폰이 100개 보다 많이 생성되는 현상은 발생하지 않게 된다.

## 문제점 알아보기
위에서 레디스를 활용해 쿠폰의 발급 개수를 가져온 후 발급이 가능하면 rdb에 저장하는 방식을 사용했다.

해당 방식에는 발급하는 쿠폰의 개수가 많이질 수록 RDB에 부하를 주게 된다.

만약 사용하는 RDB가 쿠폰 전용 DB가 아닌 다양한 곳에서 사용하고 있다면 다른 서비스까지 장애를 발생할 수 있다.

### 문제점 부가 설명
MySQL이 1분에 100개 인설트 작업만 가능하다고 가정해보자.

| 시간   | 요청                |
|-------|---------------------|
| 10:00 | 쿠폰생성 10000개 요청  |
| 10:01 | 주문 생성 요청         |
| 10:02 | 회원가입 요청          |
10시에 만개의 쿠폰 생성 요청이 들어오고 10시 1분에 주문 생성 요청, 10시 2분에 회원가입 요청이 온다면
1분에 100개씩 만개를 생성하려면 100분이 걸리게 된다.

10시 1분, 10시 2분에 들어온 주문 생성, 회원가입 요청은 100분 이후 생성이 되게 된다.

타임아웃이 없다면 느리게라도 모든 요청이 처리 되겠지만 대부분의 서비스에는 타임아웃 옵션이 설정되어 있으므로 
주문, 회원가입, 쿠폰생성 등이 오류가 발생할 수 있다.

또한 짧은 시간 내의 많은 요청은 DB 서버의 리소스를 많이 사용하게 되어 부하가 발생하게 되고 

이는 서비스 지연 or 오류로 이어 질 수 있다.

> AWS와 Ngrinder를 활용해 단기간 많은 트래픽이 들어오는 상황을 만들 수 있다.
> 
> Client -> Load Balancer -> coupon-api 들 -> MySQL
> 
> > 로드밸런서는 여러대의 애플리케이션에 트래픽을 분배할 수 있도록 도와주는 것이다.
> 
> 위의 구성은 클라이언트가 쿠폰 발급을 요청한다면 로드밸런서가 쿠폰 API에게 적절한 트래픽을 분산하고 
> 쿠폰 API 에서 MySQL을 사용하도록 한 구성이다.
> 
> > Ngrinder는 부하 테스트 툴이다. 이를 활용하면 구성한 서버에 단기간에 많은 트래픽을 발생시킬 수 있다.
> 
> AWS를 이용해 MySQL 서버의 자원 사용량을 모니터링 할 수 있고,
> 
> Ngrinder로 서버에 많은 트래픽을 발생시킬 수 있다. 
> 
> 많은 트래픽을 주면 단기간 많은 요청이 들어와 RDB의 CPU 사용량이 높아지고 
> 이로 인해 서비스의 오류로 이어지는 것을 확인할 수 있다.