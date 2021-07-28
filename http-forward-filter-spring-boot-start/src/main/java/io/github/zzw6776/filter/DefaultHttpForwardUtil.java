package io.github.zzw6776.filter;

import lombok.extern.log4j.Log4j2;
import org.apache.http.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author ZZW
 */

@Log4j2
@AutoConfigureAfter(value = HttpForwardFilter.class)
@ConditionalOnBean(HttpForwardFilter.class)
@ConditionalOnMissingBean(IHttpForwardUtil.class)
public class DefaultHttpForwardUtil implements IHttpForwardUtil{

    @Value("${httpForward.targetUri}")
    private String targetUri;

    public DefaultHttpForwardUtil() {
        log.info("init DefaultHttpForwardUtil");
    }

    @Override
    public URI getTargetUri() {
        try {
            return new URI(targetUri);
        } catch (URISyntaxException e) {
            log.error("targetUri错误", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean isForward(String Scheme, String domain, Integer port, String uri, Map<String, String> headers, String method, String contentType,String queryString) {

        return true;
    }

    @Override
    public  List<Header> getCustomerHeaders() {
        List<Header> headers = new ArrayList<>();

        return headers;
    }



}
