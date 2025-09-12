package zas.admin.zec.backend.actions.visualize;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ZasVisionVisionMessageService implements VisionMessageService {

    @Override
    public SystemMessage dataInstructionsMessage() {
        String message = """
            Please follow these formatting rules when extracting data:
            - **Date**: Format as `YYYY-MM-DD`.
            - **NAVS Number**: Use the Swiss NAVS social insurance number format `756.XXXX.XXXX.XX` (must match: `^756\\.\\d{4}\\.\\d{4}\\.\\d{2}$`).
            - **Empty Fields**: If a field is not found or empty, return an empty string (`""`).
            """;
        return new SystemMessage(message);
    }

    @Override
    public UserMessage extractTextMessage() {
        String message = """
           You are a fabulous ocr. Your task is to extract text from the given document.
           The next task will be to translate the extracted text.

           # Extraction instruction:
           Please try to keep the original format.
           
           ## Response Format
           Respond only with the extracted text with no extra information !
           """;

        return new UserMessage(message);
    }

    @Override
    public UserMessage translateMessage(String language, String textToTranslate) {
        String template = """
            <INSTRUCTIONS>
                Translate the given text to the given language.
                DO not include any extra information, only the translated text.
            </INSTRUCTIONS>
            <LANGUAGE>
                {language}
            </LANGUAGE>
            <TEXT>
                {text}
            </TEXT>
        """;

        var promptTemplate = PromptTemplate.builder()
                .template(template)
                .variables(Map.of("language", language, "text", textToTranslate))
                .build();

        return UserMessage.builder()
                .text(promptTemplate.render())
                .build();
    }

    @Override
    public UserMessage structureDataFromImageMessage(String jsonSchema) {
        String template = """
            Please extract structured data from the following scanned image document.
            Use the following output format : {format}
        """;

        var promptTemplate = PromptTemplate.builder()
                .template(template)
                .variables(Map.of("format", jsonSchema))
                .build();

        return UserMessage.builder()
                .text(promptTemplate.render())
                .build();
    }

    @Override
    public UserMessage classifyMessage() {
        String template = """
            Please classify the type of the provided document.
            Available document types: {enum}
            Respond with only one of the class names.
            Give me the type directly as string without any extra text.
        """;

        String enumValues = Arrays.stream(ZasDocumentType.Type.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));

        var promptTemplate = PromptTemplate.builder()
                .template(template)
                .variables(Map.of("enum", enumValues))
                .build();

        return UserMessage.builder()
                .text(promptTemplate.render())
                .build();
    }

    @Override
    public UserMessage fileMessage(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();

            Media media = Media.builder()
                    .id("uploaded-file")
                    .mimeType(MimeType.valueOf(file.getContentType()))
                    .data(fileBytes)
                    .build();

            return UserMessage.builder()
                    .text("") // optional text prompt
                    .media(List.of(media))
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    @Override
    public SystemMessage extractTariffPositionMessage(String jsonSchema) {
        String template = """
            <CONTEXTE>
                Vous êtes un modèle d’OCR et d’extraction d’informations. L’entrée est une image d’une page de facture.
                Les factures peuvent être des PDF numériques ou des documents scannés. Certaines peuvent contenir du bruit visuel tel que des tampons, des logos ou des notes manuscrites.
            </CONTEXTE>

            <OBJECTIF>
                Identifier et extraire toutes les prestations avec leurs codes tarifaires affichés dans la facture.
                Chaque code doit être capturé exactement tel qu’imprimé.
                L’objectif est de comparer ensuite ces codes à une liste de tarifs d’assurance.
            </OBJECTIF>

            <INSTRUCTIONS>
                1. Lire attentivement le texte de l’image de la facture.
                2. Rechercher toutes les prestations (généralement présentés dans la moitié inférieure de la facture, ligne par ligne).
                3. Ignorer toutes les autres informations liées à la prestation (montants, prix, etc.).
                4. Ignorer toutes les autres informations de l’image (souvent dans la moitié supérieure de la facture : nom, adresse, numéro de téléphone, etc.).
                5. Pour chaque prestation (ligne par ligne), retourner :
                   - la date de la prestation : format jj.mm.aaaa, ex. 19.02.2024
                   - le numero du tariff de la prestation : 3 chiffres, ex. 222, 333, etc.
                   - le code détaillé de la prestation (colonne souvent nommée « Code » ou « Chiffre Tarif ») : 5 chiffres avec un ou deux séparateurs « . » (points), parfois terminés par une lettre majuscule, ex. 4.8610, 0017.1, 4.8340.M, etc.
                   - la description de la prestation: sous le code/chiffre tarif en texte clair. Déduire les mots les plus logiques lorsque le texte est illisible.
                   - le niveau de confiance de l’extraction pour cette prestation : une valeur parmi low|medium|high
                6. Si vous n’êtes pas certain d’une extraction, l’inclure mais indiquer une confiance « low ».
                7. Retourner les résultats uniquement dans le format spécifié ci-dessous.
            </INSTRUCTIONS>

            <FORMAT_SORTIE>
                {format}
            </FORMAT_SORTIE>
        """;

        var promptTemplate = PromptTemplate.builder()
                .template(template)
                .variables(Map.of("format", jsonSchema))
                .build();

        return new SystemMessage(promptTemplate.render());
    }
}
