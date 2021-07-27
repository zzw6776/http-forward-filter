- [http-forward-filter用法](#http-forward-filter--)
    + [为什么会有这个项目](#---------)
    + [这项目能做什么?](#--------)
    + [项目实战](#----)
      - [目的](#--)
      - [背景](#--)
    + [项目用法](#----)
      - [基础用法](#----)
      - [进阶用法](#----)
      - [配置项](#---)


# http-forward-filter用法

> 部分代码参考https://github.com/mitre/HTTP-Proxy-Servlet



**项目已通过线上实际验证**


### 为什么会有这个项目

翻遍全文都没有找到能将ServletRequest进行自定义转发的方法,只能自己参照[HTTP-Proxy-Servlet](https://github.com/mitre/HTTP-Proxy-Servlet) 重新写一套,至于为什么我要将ServletRequest进行转发,可以参照实战分栏

### 这项目能做什么?

- 可以自定义转发目标uri

- 可以自定义是否需要转发

- 可以在转发时添加header


### 项目实战

#### 目的

实现spring-cloud自定义灰度方案

#### 背景

大家都知道spring-cloud是可以通过Ribbon进行负载均衡,Ribbon本身乃至他的各种衍生物如[Discovery](https://github.com/Nepxion/Discovery) 都是很优秀的现成的灰度解决方案,但是这些方案都有一个问题,都得其他项目/团队配合进行修改,在项目上游链路不清楚的情况下,这条路成本和风险非常高

在此基础下,一个最简单的灰度方案就是在不动上游业务链路调用的情况下进行自身项目一个转发,如图所示

![灰度架构图](http://processon.com/chart_image/60e2a585637689510d6eaa33.png)

项目A,项目B,项目C,项目D都为上游业务,通过feign的ribbon进行随机负载均衡,版本A为灰度版本,版本B为master版本,版本A可以通过判断条件来确定是否转发至版本B,如果版本A配置的90%的流量转发,那么实际打到版本A并且被处理的流量只有50%*10%=5%,实现新项目上线5%的灰度方案

具体部分代码如下

```Java
@Component
public class ScoreHttpForwardUtil implements IHttpForwardUtil {

    @Value("${gray.ignore-urls}")
    private String ignoreUrls;
    @Value("${gray.maxPort}")
    private Integer maxPort;
    @Value("${server.port}")
    private Integer servicePort;
    @Value("${gray.forward-percent}")
    private Integer forwardPercent;
    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${spring.application.name}")
    private String applicationName;

    @Override
    public URI getTargetUri() {
        List<ServiceInstance> forwardServicePool = discoveryClient.getInstances(applicationName).stream()
                .filter(serviceInstance -> serviceInstance.getPort() < maxPort)
                .collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(forwardServicePool)) {
            return RandomUtil.randomEle(forwardServicePool).getUri();
        }
        throw new RuntimeException("没有其他集群信息");
    }

    @Override
    public Boolean isForward(String Scheme, String domain, Integer port, String uri, Map<String, String> headers, String method, String contentType) {
        //端口不符合不转发
        if (!Objects.equals(servicePort, port)) {
            return false;
        }
        //黑名单不转发
        List<String> ignoreUrlList = Arrays.stream(StrUtil.split(StrUtil.trimToEmpty(ignoreUrls), ";"))
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(ignoreUrlList) && ignoreUrlList.stream().anyMatch(ignoreUrl -> Pattern.matches(ignoreUrl, uri))) {
            return false;
        }
        //权重判断
        if (forwardPercent > RandomUtil.randomInt(100)) {
            return false;
        }
        return true;
    }

    @Override
    public List<Header> getCustomerHeaders() {
        return null;
    }
}
```


### 项目用法

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
@Component
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
    public Boolean isForward(String Scheme, String domain, Integer port, String uri, Map<String, String> headers, String method, String contentType) {
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



