package br.com.prando.webfluxhystrix;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

@SpringBootApplication
@RestController
@EnableWebFlux
public class WebfluxHystrixApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(WebfluxHystrixApplication.class, args);
    }

    private Random random = new Random();

    @GetMapping("/message")
    public Mono<String> getMessage(@RequestParam Integer id) {
        return Mono.just("Hello World " + id).delayElement(Duration.ofMillis(random.nextInt(2000)));
    }

    @Override
    public void run(String... args) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        String commandName = "ExampleGetMessage";
        Mono<String> stringMono = WebClient.create().get().uri("http://localhost:8080/message?id=1").exchange().flatMap(clientResponse -> clientResponse.bodyToMono(String.class));
        HystrixCommands.from(stringMono).commandName(commandName).commandProperties(HystrixCommandProperties.defaultSetter().withExecutionTimeoutInMilliseconds(1500)).toFlux().singleOrEmpty().block();
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(HystrixCommandKey.Factory.asKey(commandName));
        System.out.println("SUCCESS #=" + metrics.getRollingCount(HystrixEventType.SUCCESS));
        Thread.sleep(5000);

        Flux.range(0, 50)
                .delayElements(Duration.ofMillis(200))
                .flatMap(integer -> {
                    final Mono<String> webClientMonoString = WebClient.create()
                            .get()
                            .uri("http://localhost:8080/message?id={id}", integer)
                            .retrieve()
                            .bodyToMono(String.class);

                    return HystrixCommands.from(webClientMonoString)
                            .fallback(Mono.just("Fallback"))
                            .commandName(commandName)
                            .commandProperties(HystrixCommandProperties.defaultSetter()
                                    .withExecutionTimeoutInMilliseconds(1000))
                            .toFlux().singleOrEmpty();
                })
                .subscribe(System.out::println, Throwable::printStackTrace, countDownLatch::countDown);

        countDownLatch.await();

        System.out.println("SUCCESS #=" + metrics.getRollingCount(HystrixEventType.SUCCESS));
    }
}
