
[123](#配置项)


# http-forward-filter用法

> 部分代码参考https://github.com/mitre/HTTP-Proxy-Servlet

项目默认不会进行循环转发,如在targetUri为自身的情况下,只会转发一次到自身

#### 基础用法

目前只支持spring-boot/spring-cloud

引入jar包

```JavaScript
<dependency>
  <groupId>io.github.zzw6776</groupId>
  <artifactId>http-forward-filter-spring-boot-start</artifactId>
  <version>1.0.1</version>
</dependency>
```


添加配置项

```JavaScript
httpForward.switch=true
httpForward.targetUri=http://127.0.0.1:8081
```


以上配置项意思为开启转发,并转发所有请求到[http://127.0.0.1:8081](http://127.0.0.1:8081)


#### 进阶用法

引入jar包

```JavaScript
<dependency>
  <groupId>io.github.zzw6776</groupId>
  <artifactId>http-forward-filter-spring-boot-start</artifactId>
  <version>1.0.1</version>
</dependency>
```


添加配置项

```JavaScript
httpForward.switch=true
```


实现接口IHttpForwardUtil,并注入spring容器,例子如下

```Java
public class CustomerHttpForwardUtil implements IHttpForwardUtil {


    public CustomerHttpForwardUtil() {
      log.info("init CustomerHttpForwardUtil");
    }

    /**
     * 目标uri,需是完整域名,包含http/https传输协议及端口号
     * @return
     */
    @Override
    public URI getTargetUri() {
        return null;
    }

    /**
     * 是否进行转发
     * @param Scheme 传输协议
     * @param domain 域名
     * @param port 端口号
     * @param uri 请求uri
     * @param headers header
     * @param method 网络协议(get/post..)
     * @param contentType contentType
     * @return
     */
    @Override
    public Boolean isForward(String Scheme, String domain, Integer port, String uri, Map<String, String> heades, String method, String contentType) {
        return null;
    }

    /**
     * 需要自定义的header
     * @return
     */
    @Override
    public List<Header> getCustomerHeaders() {
        return null;
    }
}
```


这样就能自定义转发地址,是否转发,自定义header

#### 配置项

:后为默认值

```Java
//httpClient相关,可参考httpClient官方文档
httpForward.httpclient.doHandleRedirects:false
httpForward.httpclient.connectTimeout:-1
httpForward.httpclient.readTimeout:-1
httpForward.httpclient.connectionRequestTimeout:-1
httpForward.httpclient.maxConnections:-1
//是否转发#号后面参数
httpForward.doSendUrlFragment:true
//是否保存host若为true则保持入参的host,慎用,会导致转发失效
httpForward.doPreserveHost:false
//是否保存cookie 若为true会保存转发后的cookie
httpForward.doPreserveCookies:true
//为转发请求设置header: X-Forwarded-Proto,禁止循环转发通过该参数实现
httpForward.doForwardIP:true

```


