package com.itstyle.seckill.web;

import com.itstyle.seckill.common.redis.RedisUtil;
import com.itstyle.seckill.queue.kafka.KafkaSender;
import com.tencentcloudapi.captcha.v20190722.CaptchaClient;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaResultRequest;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaResultResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import java.util.List;

import javax.jms.Destination;
import javax.servlet.http.HttpServletRequest;

import org.apache.activemq.command.ActiveMQQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.itstyle.seckill.common.entity.Result;
import com.itstyle.seckill.common.entity.Seckill;
import com.itstyle.seckill.common.utils.HttpClient;
import com.itstyle.seckill.common.utils.IPUtils;
import com.itstyle.seckill.queue.activemq.ActiveMQSender;
import com.itstyle.seckill.service.ISeckillService;

@Api(tags = "秒杀商品")
@RestController
@RequestMapping("/seckillPage")
public class SeckillPageController {

    private final static Logger LOGGER = LoggerFactory.getLogger(SeckillPageController.class);

    @Autowired
	private ISeckillService seckillService;
	
	@Autowired
	private ActiveMQSender activeMQSender;

    @Autowired
    private KafkaSender kafkaSender;

    @Autowired
    private RedisUtil redisUtil;

    @Value("${qq.captcha.endPoint}")
    private String endPoint;
    @Value("${qq.captcha.SecretId}")
    private String secretId;
    @Value("${qq.captcha.SecretKey}")
    private String secretKey;
	@Value("${qq.captcha.aid}")
	private String aid;
	@Value("${qq.captcha.AppSecretKey}")
	private String appSecretKey;
	
	
	@ApiOperation(value = "秒杀商品列表", nickname = "小柒2012")
	@PostMapping("/list")
	public Result list() {
		//返回JSON数据、前端VUE迭代即可
		List<Seckill>  List = seckillService.getSeckillList();
		return Result.ok(List);
	}
	
	@PostMapping("/startSeckill")
    public Result  startSeckill(String ticket,String randstr,HttpServletRequest request) {
        try {
            Credential cred = new Credential(secretId, secretKey);
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint(endPoint);
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            CaptchaClient client = new CaptchaClient(cred, "", clientProfile);
            DescribeCaptchaResultRequest req = new DescribeCaptchaResultRequest();
            req.setCaptchaType(9L);
            req.setTicket(ticket);
            req.setUserIp(IPUtils.getIpAddr());
            req.setRandstr(randstr);
            req.setCaptchaAppId(Long.valueOf(aid));
            req.setAppSecretKey(appSecretKey);
            DescribeCaptchaResultResponse resp = client.DescribeCaptchaResult(req);
//            {"CaptchaCode":1,"CaptchaMsg":"OK","EvilLevel":0,"GetCaptchaTime":0,"RequestId":"8f11aabf-d319-4884-b6d3-8fa6fa561418"}
            /**
             * CaptchaCode: 1:验证成功，0:验证失败，100:AppSecretKey参数校验错误[required]
             * EvilLevel:[0,100]，恶意等级[optional]
             * CaptchaMsg:验证错误信息[optional]
             */
            JSONObject json = JSONObject.parseObject(DescribeCaptchaResultResponse.toJsonString(resp));
            Integer response = (Integer) json.get("CaptchaCode");
            if(1 == response) {
                //进入队列、假数据而已
                kafkaSender.sendChannelMess("seckill", 1000+";"+2);
//                Destination destination = new ActiveMQQueue("seckill.queue");
//                activeMQSender.sendChannelMess(destination,1000+";"+1);
                return Result.ok();
            }else{
                return Result.error("验证失败");
            }
        } catch (TencentCloudSDKException e) {
            return Result.error("验证失败");
        }
    }

    @ApiOperation(value="最佳实践)",nickname="爪哇笔记")
    @PostMapping("/startRedisCount")
    public Result startRedisCount(long secKillId,long userId){
        /**
         * 原子递减
         */
        long number = redisUtil.decr(secKillId+"-num",1);
        if(number>=0){
            seckillService.startSeckilDBPCC_TWO(secKillId, userId);
            LOGGER.info("用户:{}秒杀商品成功",userId);
        }else{
            LOGGER.info("用户:{}秒杀商品失败",userId);
        }
        return Result.ok();
    }
}
