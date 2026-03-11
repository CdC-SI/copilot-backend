package zas.admin.zec.backend.actions.summarize;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmStudySynthesisService {

    private final ChatModel chatModel;

    public LlmStudySynthesisService(@Qualifier("internalChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String summarizeStudies(List<String> studyCertificates) {
        String prompt = """
                Tu es expert en analyse de certificat d’études.
                Le but de cette analyse est de reconstituer la carrière scolaire d'une personne, ou des enfants d’un assuré, à partir de plusieurs certificats d'études.
                Pour chaque certificat d'étude (<STUDY_CERTIFICATE>), tu dois extraire les informations suivantes :
                - Le titulaire du certificat (nom et prénom)
                - Le niveau d'étude (par exemple : école primaire, secondaire I, secondaire II, universitaire, etc.)
                - La date de début et de fin de chaque niveau d'étude
                - Le nom de l'établissement scolaire
                - Les diplômes ou certificats obtenus
                - Toute mention spéciale ou distinction reçue
                
                Après avoir extrait ces informations, tu dois les organiser de manière chronologique pour reconstituer la carrière scolaire complète de la personne ou de la famille.
                Ordonne ces informations dans un tableau structuré, un tableau par personne si plusieurs personnes sont concernées.
                Fournis un résumé clair et concis de cette carrière scolaire, en mettant en évidence les étapes clés et les réalisations importantes.
                Voici les certificats d'études à analyser :
                
                <STUDY_CERTIFICATES>
                    %s
                </STUDY_CERTIFICATES>
                
                Fournis le résumé de la carrière scolaire en français, en traduisant au besoin les informations extraites des certificats.
                """;

        StringBuilder certificatesBuilder = new StringBuilder();
        for (String certificate : studyCertificates) {
            certificatesBuilder.append("<STUDY_CERTIFICATE>\n")
                               .append(certificate)
                               .append("\n</STUDY_CERTIFICATE>\n");
        }

        var userMessage = UserMessage.builder()
                .text(String.format(prompt, certificatesBuilder))
                .build();

        return chatModel
                .call(new Prompt(userMessage))
                .getResult()
                .getOutput()
                .getText();
    }
}
