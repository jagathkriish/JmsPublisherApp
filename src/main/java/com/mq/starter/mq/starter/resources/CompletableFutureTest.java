package com.mq.starter.mq.starter.resources;

import org.springframework.jms.core.JmsTemplate;
import javax.jms.Queue;
//import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
//import java.util.concurrent.TimeUnit;

public class CompletableFutureTest {

    CompletableFuture<Object> getJmsResponseMessage(String corId, JmsTemplate tmpl1, JmsTemplate tmpl2, Queue resQueue) {
        return CompletableFuture.supplyAsync(() -> {
            if(corId.equals("exception")){
                return "Exception in publishing the message";
            }else {
                tmpl1.setReceiveTimeout(4000);
                tmpl2.setReceiveTimeout(4000);

                System.out.println(tmpl1.hashCode() + " hashcode of jms template1");
                Object responseMessage = tmpl1
                        .receiveSelectedAndConvert(resQueue,
                                "JMSCorrelationID='" + corId + "'");
                System.out.println("recieved message from server1 with correlation Id " + corId + " and message " + responseMessage + "  " + tmpl1.hashCode());
                return responseMessage+" from server 1";
            }
        }).handleAsync((res, ex) -> {
            if(ex != null) {
                System.out.println("Oops! We have an exception recieving msg from jms server - 1 - and now trying with server - 2 " + ex.getMessage());
                try{
                    String responseMessage2 = (String) tmpl2
                            .receiveSelectedAndConvert(resQueue,
                                    "JMSCorrelationID='" + corId + "'");
                    System.out.println("recieved message from server2 with correlation Id "+corId+" and message "+responseMessage2+"  "+tmpl2.hashCode());
                    return responseMessage2+" from server 2";
                }catch(Exception e){
                    res = "Exception while recieving message";
                    System.out.println("Error recieving message from server2");
                    return "Error Result from server msg 2";
                }
            }
            return res;
        });
    };

    CompletableFuture<String> putJmsRequestMessage(String corrId, JmsTemplate tmpl1, JmsTemplate tmpl2, Queue reqQueue, Queue resQueue, String msg) {

        return CompletableFuture.supplyAsync(() -> {
                /*int randomVal = new Random().nextInt(30);
                System.out.println(randomVal+"  from async");
                TimeUnit.SECONDS.sleep(randomVal);*/
            tmpl1.convertAndSend(reqQueue, msg,
                    new CorrelationIdPostProcessor(corrId));
            System.out.println("Published message to request queue of server1 with message " + msg + " and correlation Id " + corrId);
            return corrId;
        }).handleAsync((res, ex) -> {
            if (ex != null) {
                String response = "";
                System.out.println("Oops! We have an exception publishing message to jms server - 1 - " + ex.getMessage());
                try {
                    tmpl2.convertAndSend(reqQueue, msg,
                            new CorrelationIdPostProcessor(corrId));
                    System.out.println("Published message to request queue of server2 with message " + msg + " and correlation Id " + corrId);
                    response = corrId;
                } catch (Exception e) {
                    response = "exception";
                    System.out.println("Error publishing message to server2");
                }finally {
                    return response;
                }
            }
            return res;
        });
    }

    public Object subscribeAndGet(String corrId, JmsTemplate tmpl1, JmsTemplate tmpl2, Queue reqQueue, Queue resQueue, String msg) throws ExecutionException, InterruptedException, TimeoutException {
        long start = System.currentTimeMillis();
        CompletableFuture<Object> jmsOperation = putJmsRequestMessage(corrId, tmpl1, tmpl2, reqQueue, resQueue, msg).thenCompose(response -> getJmsResponseMessage(response, tmpl1, tmpl2, resQueue));
        Object res = jmsOperation.get(10000, TimeUnit.MILLISECONDS);
        System.out.println(start-System.currentTimeMillis()+"   System takes  ... ");
        return res;
    }
}
