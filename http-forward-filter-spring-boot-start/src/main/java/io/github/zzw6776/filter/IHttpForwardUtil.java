package io.github.zzw6776.filter;

import org.apache.http.Header;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * @author ZZW
 */
public interface IHttpForwardUtil {

    /**
     * 目标uri,需是完整域名,包含http/https传输协议及端口号
     * @return
     */
    public URI getTargetUri();

    /**
     * 是否进行转发
     * @param Scheme 传输协议
     * @param domain 域名
     * @param port 端口号
     * @param uri 请求uri
     * @param headers header
     * @param method 网络协议(get/post..)
     * @param contentType contentType
     * @param queryString queryString
     * @return
     */
    public Boolean isForward(String Scheme, String domain, Integer port, String uri, Map<String, String> headers, String method, String contentType,String queryString);

    /**
     * 需要自定义的header
     * @return
     */
    List<Header> getCustomerHeaders();
}
