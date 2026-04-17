Spring Boot와 Mustache로 간단한 화면을 띄웠는데, 한글이 `???` 형태로 깨지는 문제가 발생했다.

![🤔 왜 깨질까?](/assets/spring-boot-2-7-mustache-encoding/browser-korean-text-broken-question-marks.png)

처음에는 단순히 파일 인코딩이나 IntelliJ 설정 문제라고 생각했다.

하지만 HTML의 `meta charset`, IDE 인코딩 설정을 모두 확인해도 문제는 그대로였다.

알고 보니 파일 인코딩 문제가 아니라, 응답 헤더의 `Content-Type`에 `charset=UTF-8`이 들어가지 않아서 발생한 문제였다.

---

### 처음 의심했던 원인들

#### 1.**HTML의 `meta charset` 문제**

가장 먼저 확인한 것은 템플릿의 `charset` 선언이었다.

```
<!DOCTYPE HTML>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    ...
```

하지만 이 설정은 이미 들어가 있었고,

추가로 확인해도 문제는 그대로였다.

#### 2.**IntelliJ 파일 인코딩 문제**

다음으로는 IntelliJ의 프로젝트 인코딩, 파일 인코딩, 저장 인코딩을 확인했다.

이 부분은 아래 사진과 같이 설정되어 있었다.

![Settings → Editor → File Encodings에 ISO-8859-1로 설정되어 있는 모습](/assets/spring-boot-2-7-mustache-encoding/intellij-file-encoding-iso8859-1-settings.png)

그래서 Editor → File Encodings에서 인코딩을 UTF-8로 설정했지만, 문제는 여전했다.

#### 3.**서버 인코딩 문제**

그다음에는 서버 인코딩 설정도 확인했다.

- 웹 서버는 클라이언트에게 데이터를 전송할 때 사용하는 인코딩 설정에 따라 데이터를 전송한다. 이 설정이 올바르지 않으면 클라이언트는 잘못 인코딩된 데이터를 받을 수 있다. 특히, Spring Boot 2.7.0 ~ 2.7.13 버전(현재 사용 중인 버전)에서 mustache와 함께 사용할 때 **한글 깨짐 이슈**가 발생한다고 알려져 있었다.
- 관련 이슈 링크:https://github.com/spring-projects/spring-boot/issues/32472

### **해결 방법**

1. **`application.properties`** 설정 추가

```
server.servlet.encoding.charset=UTF-8
server.servlet.encoding.force=true
```

- **`server.servlet.encoding.charset=UTF-8`**: 서버가 처리하는 모든 요청과 응답에 대한 문자 인코딩을 UTF-8로 설정한다는 것을 의미한다.
- **`server.servlet.encoding.force=true`**: 서버가 생성하는 모든 응답의 문자 인코딩을 `**server.servlet.encoding.charset`**설정값(위 예제에서는 UTF-8)으로 강제한다는 것을 의미한다.

2.**Spring Boot 버전 다운그레이드**`build.gradle`의 plugins 섹션에서 Spring Boot 버전을 2.6.x로 변경한다.

```
plugins {
	id 'java'
	id 'org.springframework.boot' version '2.6.15'
	id 'io.spring.dependency-management' version '1.0.15.RELEASE'
}
```

#### 3.**다른 템플릿 엔진 사용**

Mustache와 Spring Boot 연동 시 문제가 발생하기 때문에, 이를 피하기 위해 다른 템플릿 엔진의 사용을 고려해볼 수 있다. 예로, Thymeleaf를 사용할 수 있다.

### **진짜 원인**

![](/assets/spring-boot-2-7-mustache-encoding/chrome-network-content-type-iso-8859-1.png)

우선 개발자 모드를 열어서 Content-Type을 확인해보았는데, ISO-8859-1로 되어있었다.

HTML에서도 인텔리제이에서도 설정을 해두었는데 깨지는 거라면, Spring 내부 어디선가 바꾼다고 생각하게 되었는데 어디서 수정하는지를 찾아보았다.

그 과정에서 `MustacheViewResolver`의 존재를 알게 되었다. 하지만

![](/assets/spring-boot-2-7-mustache-encoding/mustache-view-resolver-class.png)

이곳에서도 `ContentType`에서 변경하는 것은 없었다.

그러다 문득, 해결 방법 중 'Spring Boot 버전 다운그레이드'가 있어 버전을 올리다가 수정사항이 생겨 문제가 발생했나? 라는 생각을 하게 되었다.

그래서 [SpringBoot 공식 깃허브](https://github.com/spring-projects/spring-boot)에서 커밋 기록을 뒤지기 시작했고,

![](/assets/spring-boot-2-7-mustache-encoding/mustache-servlet-web-configuration-git-diff.png)

![](/assets/spring-boot-2-7-mustache-encoding/spring-boot-commit-mustache-properties-servlet-specific.png)

`MustacheServletWebConfiguration`에서 많은 변경이 있는 것을 발견했다.

왜 이런 변경이 발생했을까?

[Mustache 설정이 Reactive 환경(Spring WebFlux)에서 제대로 동작하지 않는다.](https://github.com/spring-projects/spring-boot/issues/28858) 는 이슈에서 Servlet 전용 Mustache 속성을 `spring.mustache.servlet.*`로 분리해 의미를 명확히 하고, reactive 쪽과도 구조를 정리하려고 했다고 한다.

그 과정에서 `MustacheProperties`가 `AbstractTemplateViewResolverProperties`를 더 이상 상속하지 않게 되었고, `applyToMvcViewResolver()`도 사라졌다.

![](/assets/spring-boot-2-7-mustache-encoding/abstract-template-view-resolver-properties-applytomvcviewresolver.png)

![](/assets/spring-boot-2-7-mustache-encoding/abstract-view-resolver-properties-add-charset-parameter.png)

`AbstractTemplateViewResolverProperties`에서 `getContentType에는 사진과 같이 charset을 파라미터에 넣는 것을 볼 수 있다.

참고로 this.charset은 전역변수로 `private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;` 이렇게 설정 되어 있다.

`AbstractTemplateViewResolverProperties`의 `applyToMvcViewResolver()` 메서드와 `MustacheServletWebConfiguration`의 `mustacheViewResolver` 메서드는 같아 보이지만 큰 차이가 있다.

![](/assets/spring-boot-2-7-mustache-encoding/mustache-servlet-web-configuration-vs-applytomvcviewresolver.png)

`mustacheViewResolver`의 `getContentType()`을 따라가보면 `MustacheProperties`의 `getContentType()`과 `AbstractTemplateViewResolverProperties` 반환 방식의 차이를 알 수 있다.

![](/assets/spring-boot-2-7-mustache-encoding/thymeleaf-auto-configuration-set-character-encoding.png)

또한 `this.contentType`을 따라가보면

```
private MimeType contentType = DEFAULT_CONTENT_TYPE;

private static final MimeType DEFAULT_CONTENT_TYPE = MimeType.valueOf("text/html");
```

이렇게 설정되어있는데 charset이 빠져있는 것을 알 수 있다. charset이 빠져있다면 Servlet 규칙으로 인하여 자동으로 charset을 ISO-8859-1로 설정해주는 것이었다!

여담으로 `Thymeleaf`는 해당 오류가 발생하지 않는데, 이유는 다음과 같다.

Spring 실행 시점에`ThymeleafAutoConfiguration`의 `defaultTemplateResolver()`메서드에 가보면 아래와 같은 부분을 찾을 수 있다.

![](/assets/spring-boot-2-7-mustache-encoding/thymeleaf-view-resolver-set-content-type.png)

`properties`는 `private final ThymeleafProperties properties;`로 되어 있으며, `

`private static final Charset DEFAULT_ENCODING = StandardCharsets.UTF_8;` 즉 기본적으로 인코딩이 UTF-8로 설정 되어있다.

![](https://blog.kakaocdn.net/dna/cMrmWp/dJMcajaBTe3/AAAAAAAAAAAAAAAAAAAAAOh-dcNoCqzjXhoRlStNTkvUvmOKChvdn3hzTdlninaG/img.png?credential=yqXZFxpELC7KVnFOS48ylbz2pIh7yKj8&expires=1777561199&allow_ip=&allow_referer=&signature=EH0qT%2BX%2BgHsp0XdPO630j5H6Y%2FY%3D)

사진과 같이 setContentType으로 ContentType에 Chertext/html;charset=UTF-8까지 들어가기 때문에 `Thymeleaf`는 해당 오류가 발생하지 않음을 알 수 있었다.