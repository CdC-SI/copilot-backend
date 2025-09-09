package zas.admin.zec.backend.actions.visualize;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.tools.JsonSchemaBuilder;

import java.util.List;

@Service
public class ZasVisionService implements VisionService {
    private final ChatClient chatClient;
    private final VisionMessageService visionMessageService;


    public ZasVisionService(@Qualifier("visionModel") ChatModel visionModel, VisionMessageService visionMessageService) {
        this.chatClient = ChatClient.create(visionModel);
        this.visionMessageService = visionMessageService;
    }

    public JsonNode extractFieldsFromFile(MultipartFile file, List<String> fields) {
        String jsonSchema = JsonSchemaBuilder.buildFlatJsonSchema(fields);

        var options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                .build();

        var prompt = new Prompt(
                visionMessageService.structureDataFromImageMessage(jsonSchema),
                visionMessageService.dataInstructionsMessage(),
                visionMessageService.fileMessage(file));

        return chatClient.prompt(prompt).options(options).call().entity(JsonNode.class);
    }


    public MedicalServices extractTariffPositionsFromFile(MultipartFile file) {
        String jsonSchema = JsonSchemaGenerator.generateForType(MedicalServices.class);

        var options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                .build();

        var prompt = new Prompt(
                visionMessageService.extractTariffPositionMessage(jsonSchema),
                visionMessageService.fileMessage(file));

        return chatClient.prompt(prompt).options(options).call().entity(MedicalServices.class);
    }



    @Override
    public ZasDocumentType classifyFile(MultipartFile file) {
        String jsonSchema = JsonSchemaGenerator.generateForType(ZasDocumentType.class);
        var options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                .build();

        var prompt = new Prompt(visionMessageService.classifyMessage(), visionMessageService.fileMessage(file));

        return chatClient.prompt(prompt).options(options).call().entity(ZasDocumentType.class);
    }

    @Override
    public TextTranslation translateFile(MultipartFile file, String language) {
        var prompt = new Prompt(visionMessageService.translateMessage(language), visionMessageService.fileMessage(file));
        var response = chatClient.prompt(prompt).call().content();

        return new TextTranslation(response);
    }
}
