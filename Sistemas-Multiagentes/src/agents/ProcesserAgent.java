package agents;

import agents.PopulationData;

import jade.core.Agent;
import jade.core.behaviours.ParallelBehaviour;

import java.io.IOException;
import java.util.ArrayList;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.AchieveREResponder;
import jade.proto.AchieveREInitiator;

import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.FailureException;

public class ProcesserAgent extends Agent {

    final static String DASHBOARD = "Dashboard";
    final static String PROCESSER = "Processer";

    final static int N_WEB_AGENTS = 3;

    private ArrayList<ArrayList<PopulationData>> info = new ArrayList<ArrayList<PopulationData>>();
    private RequestReceiver requestReceiver;

    protected void setup() {

        MessageTemplate protocol = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
        MessageTemplate performative = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
        MessageTemplate sender = MessageTemplate.MatchSender(new AID(DASHBOARD, AID.ISLOCALNAME));
        MessageTemplate temp = MessageTemplate.and(protocol, performative);
        temp = MessageTemplate.and(temp, sender);

        requestReceiver = new RequestReceiver(this, temp);
        addBehaviour(requestReceiver);
    }

    protected void takeDown() {
        System.out.println("[PROCESSER] Taking down...");
    }

    public class RequestReceiver extends AchieveREResponder {

        public RequestReceiver(Agent a, MessageTemplate temp) {
            super(a, temp);
        }

        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {

            System.out.println("[PROCESSER-AGENT] " + request.getSender().getName() + " has sent a request.");
            ArrayList<String> msgContent = null;

            try {
                msgContent = (ArrayList<String>) request.getContentObject();
            } catch (UnreadableException e) {
                throw new NotUnderstoodException("[PROCESSER-AGENT] Unreadable content of the message.");
            }

            if (msgContent.size() == N_WEB_AGENTS) {

                ParallelBehaviour pb = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
                String webAgentName, webAgentUrl;

                for (String content : msgContent) {

                    String[] contentParts = content.split(":");
                    webAgentName = contentParts[0];
                    webAgentUrl = contentParts[1];

                    System.out.println(
                            "[PROCESSER-AGENT] URL: " + webAgentUrl + " will be processed by: " + webAgentName);

                    ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                    msg.addReceiver(new AID((String) webAgentName, AID.ISLOCALNAME));
                    msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    msg.setSender(new AID(PROCESSER, AID.ISLOCALNAME));
                    msg.setContent(webAgentUrl);

                    pb.addSubBehaviour(new RequestInitiator(this, msg));
                }

                addBehaviour(pb);

            } else {
                throw new RefuseException("[PROCESSER-AGENT] The message content contains information for "
                        + msgContent.size() + " web agents but " + N_WEB_AGENTS + " web agents are needed!");
            }

            this.block();

            ACLMessage agree = request.createReply();
            agree.setPerformative(ACLMessage.AGREE);
            return agree;
        }

        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response)
                throws FailureException {

            System.out.println("[PROCESSER-AGENT] Preparing result...");
            ACLMessage inform = request.createReply();
            inform.setPerformative(ACLMessage.INFORM);

            try {
                inform.setContentObject(info);
            } catch (IOException e) {
                throw new FailureException("[PROCESSER-AGENT] FailureException: serialization error.");
            }

            return inform;
        }

    }

    public class RequestInitiator extends AchieveREInitiator {

        private int count = 0;

        public RequestInitiator(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        protected void handleAgree(ACLMessage agree) {
            System.out.println("[PROCESSER] " + agree.getSender().getName() + " has accepted the request.");
        }

        protected void handleRefuse(ACLMessage refuse) {
            System.out.println("[PROCESSER] " + refuse.getSender().getName() + " has rejected the request.");
        }

        protected void handleNotUnderstood(ACLMessage notUnderstood) {
            System.out
                    .println("[PROCESSER] " + notUnderstood.getSender().getName() + " didn't understood the request.");
        }

        protected void handleInform(ACLMessage inform) {

            count++;
            System.out.println("[PROCESSER] Received results from " + inform.getSender().getName());

            ArrayList<PopulationData> table = null;
            try {
                table = (ArrayList<PopulationData>) inform.getContentObject();
            } catch (UnreadableException e) {
                System.out.println(e.getMessage());
            }

            info.add(table);

            if (count >= N_WEB_AGENTS) {
                requestReceiver.restart();
            }
        }

        protected void handleFailure(ACLMessage failure) {
            System.out.println("[PROCESSER] " + failure.getSender().getName() + " has failed!");
        }

    }

}