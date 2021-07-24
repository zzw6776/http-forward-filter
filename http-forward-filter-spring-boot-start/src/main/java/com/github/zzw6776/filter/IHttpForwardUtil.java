package com.github.zzw6776.filter;

import org.apache.http.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.net.URI;
import java.util.List;
import java.util.Map;


public interface IHttpForwardUtil {

    public URI getTargetUri();

    public Boolean isForward(String Scheme, String domain, Integer port, String uri, Map<String, String> heades, String method, String contentType);

    List<Header> getCustomerHeaders();
}
