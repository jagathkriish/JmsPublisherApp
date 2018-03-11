package com.mq.starter.mq.starter.resources;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jms.core.MessagePostProcessor;

import java.util.Random;
import java.util.UUID;
import javax.jms.JMSException;

import javax.jms.Message;

import javax.jms.Queue;

@RestController
public class ProducerResource {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private JmsTemplate jmsTemplate2;

    @Autowired
    private Queue reqQueue;

    @Autowired
    private Queue resQueue;

    @GetMapping("/create/{message}")
    public String publish(@PathVariable("message") final String message) {
        final String correlationId = UUID.randomUUID().toString();

        System.out.println(reqQueue.hashCode()+"  "+correlationId);

        int rand  = new Random().nextInt(2);
        if(rand == 0){
            jmsTemplate.convertAndSend(reqQueue, message,
                    new CorrelationIdPostProcessor(correlationId));
            System.out.println("Published message to request queue of server1 with message "+message+" and correlation Id "+correlationId);
        }else{
            jmsTemplate2.convertAndSend(reqQueue, message,
                    new CorrelationIdPostProcessor(correlationId));
            System.out.println("Published message to request queue of server2 with message "+message+" and correlation Id "+correlationId);
        }

        jmsTemplate.setReceiveTimeout(4000);
        jmsTemplate2.setReceiveTimeout(4000);

        String responseMessage = (String) jmsTemplate
                    .receiveSelectedAndConvert(resQueue,
                            "JMSCorrelationID='" + correlationId + "'");

        String responseMessage2 = (String) jmsTemplate2
                .receiveSelectedAndConvert(resQueue,
                        "JMSCorrelationID='" + correlationId + "'");

        return "Published OK with message from server1: "+responseMessage+" and from server2: "+responseMessage2;
    }

    private class CorrelationIdPostProcessor implements MessagePostProcessor {
        private final String correlationId;

        public CorrelationIdPostProcessor(final String correlationId) {
            this.correlationId = correlationId;
        }

        @Override
        public Message postProcessMessage(final Message msg)
                throws JMSException {
            msg.setJMSCorrelationID(correlationId);
            return msg;
        }
    }
}
