package com.twilio;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.port;
import static spark.Spark.staticFileLocation;
import static spark.Spark.afterAfter;

import java.util.HashMap;

import com.github.javafaker.Faker;
import com.google.gson.Gson;

// Token generation imports
import com.twilio.jwt.accesstoken.AccessToken;
import com.twilio.jwt.accesstoken.VoiceGrant;

// TwiML generation imports
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Dial;
import com.twilio.twiml.voice.Number;
import com.twilio.twiml.voice.Client;
import com.twilio.twiml.voice.Say;

public class Webapp {
    
    public static String generateIdentity() {
        // Create a Faker instance to generate a random username for the connecting user
        Faker faker = new Faker();
        return faker.name().firstName() + faker.address().zipCode();
    }

    public static String createJsonAccessToken(String identity) {
        String acctSid = System.getenv("TWILIO_ACCOUNT_SID");
        String applicationSid = System.getenv("TWILIO_TWIML_APP_SID");
        String apiKey = System.getenv("API_KEY");
        String apiSecret = System.getenv("API_SECRET");
        // Create Voice grant
        VoiceGrant grant = new VoiceGrant();
        grant.setOutgoingApplicationSid(applicationSid);
        
        // Optional: add to allow incoming calls
        grant.setIncomingAllow(true);
        
        // Create access token
        AccessToken accessToken = new AccessToken.Builder(acctSid, apiKey, apiSecret)
                .identity(identity)
                .grant(grant)
                .build();
        
        String token = accessToken.toJwt();
        
        // create JSON response payload
        HashMap<String, String> json = new HashMap<>();
        json.put("identity", identity);
        json.put("token", token);

        Gson gson = new Gson();
        return gson.toJson(json);
    }

    private static boolean isPhoneNumber(String to) {
        return to.matches("^[\\d\\+\\-\\(\\) ]+$");
    }

    private static Dial.Builder addChildReceiver(Dial.Builder builder, String to) {
        // wrap the phone number or client name in the appropriate TwiML verb
        // by checking if the number given has only digits and format symbols
        if (Webapp.isPhoneNumber(to)) {
            return builder.number(new Number.Builder(to).build());
        }
        return builder.client(new Client.Builder(to).build());
    }
    
    public static String createVoiceResponse(String to, String identity) {
        VoiceResponse voiceTwimlResponse;
        if (to == System.getenv("TWILIO_CALLER_ID")) {
            System.out.println("In first condition");
            Client client = new Client.Builder(identity).build();
            Dial dial = new Dial.Builder().client(client).build();
            voiceTwimlResponse = new VoiceResponse.Builder().dial(dial).build();
        }
        else if (to != null) {
            System.out.println("In condition where to != null");
            System.out.println(to);
            System.out.println(identity);
            Dial.Builder dialBuilder = new Dial.Builder()
                    .callerId(System.getenv("TWILIO_CALLER_ID"));

            Dial.Builder dialBuilderWithReceiver = Webapp.addChildReceiver(dialBuilder, to);

            voiceTwimlResponse = new VoiceResponse.Builder()
                    .dial(dialBuilderWithReceiver.build())
                    .build();
        } else {
            voiceTwimlResponse = new VoiceResponse.Builder()
                    .say(new Say.Builder("Thanks for calling!").build())
                    .build();
        }

        return voiceTwimlResponse.toXml();
    }

    public static void main(String[] args) {
        // Default port 8080
        port(8080);

        // Serve static files from src/main/resources/public
        staticFileLocation("/public");

        // Log all requests and responses
        afterAfter(new LoggingFilter());

        get("/twilio.min.js", (request, response) -> {
            response.redirect("/node_modules/@twilio/voice-sdk/dist/twilio.min.js");
            return response;
        });

        // Creating an in-memory store to keep track of the most recent identity,
        // so we can route incoming calls to the most recent browser device created.
        String[] identity = {""};

        // Create a capability token using our Twilio credentials
        get("/token", "application/json", (request, response) -> {
            // Generate a random username for the connecting client
            String randomIdentity = Webapp.generateIdentity();
            identity[0] = randomIdentity;

            // Render JSON response
            response.header("Content-Type", "application/json");
            return Webapp.createJsonAccessToken(identity[0]);
        });

        // Generate voice TwiML
        post("/voice", "application/x-www-form-urlencoded", (request, response) -> {
            String to = request.queryParams("To");
            if (to != null) {
                to = request.queryParams("phone");
            }

            response.header("Content-Type", "text/xml");
            return Webapp.createVoiceResponse(to, identity[0]);
        });
    }
}
