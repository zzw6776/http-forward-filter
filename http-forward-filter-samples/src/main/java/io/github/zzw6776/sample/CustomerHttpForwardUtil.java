package io.github.zzw6776.sample;

import io.github.zzw6776.filter.IHttpForwardUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.http.Header;

import java.net.URI;
import java.util.List;
import java.util.Map;
//@Component
@Log4j2
public class CustomerHttpForwardUtil implements IHttpForwardUtil {


    public CustomerHttpForwardUtil() {
      log.info("init CustomerHttpForwardUtil");
    }

    @Override
    public URI getTargetUri() {
        return null;
    }

    @Override
    public Boolean isForward(String Scheme, String domain, Integer port, String uri, Map<String, String> heades, String method, String contentType) {
        return null;
    }

    @Override
    public List<Header> getCustomerHeaders() {
        return null;
    }
}
