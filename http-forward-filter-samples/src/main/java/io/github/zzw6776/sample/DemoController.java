package io.github.zzw6776.sample;

import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * @author ZZW
 */
@RestController
@Log4j2
public class DemoController {


    /**
     * 此种方式接收带在url的参数,包括不仅限get请求,form-data的标准参数请求
     * @param param
     * @return
     */
    @RequestMapping("/testRequestParam")
    @ResponseBody
    public String testRequestParam(@RequestParam("param") String param, HttpServletResponse response) {
        response.addCookie(new Cookie("123","123"));
        log.info(param);
        return param;
    }

    /**
     * 此种方式只能接收raw的json入参
     * @param demoParam
     * @return
     */
    @RequestMapping("/testRequestBodyForJson")
    @ResponseBody
    public Object testRequestBodyForJson(@RequestBody DemoParam demoParam) {
        log.info(demoParam);
        return demoParam;
    }

    /**
     * 此种方式可接收form-data
     * @param demoParam
     * @param multipartFile
     * @return
     */
    @RequestMapping("/testMultipartFile")
    @ResponseBody
    public Object testMultipartFile(DemoParam demoParam, @RequestParam("file") MultipartFile multipartFile) {
        log.info("multipartFile.getSize()"+multipartFile.getSize());
        log.info(demoParam);
        return demoParam;
    }

    /**
     * 此种方式可接收x-www-form及raw的所有入参
     * @param body
     * @return
     */
    @RequestMapping("/testRequestBody")
    @ResponseBody
    public String testRequestBody(@RequestBody String body)  {
        log.info(body);
        return body;
    }
}
