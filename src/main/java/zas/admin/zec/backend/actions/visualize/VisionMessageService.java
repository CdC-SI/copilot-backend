package zas.admin.zec.backend.actions.visualize;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.multipart.MultipartFile;

public interface VisionMessageService {
     SystemMessage dataInstructionsMessage();

     UserMessage structureDataFromImageMessage(String jsonSchema);

     UserMessage classifyMessage();

     UserMessage fileMessage(MultipartFile file);

     UserMessage translateMessage(String language);

     SystemMessage extractTariffPositionMessage(String jsonSchema);
}
