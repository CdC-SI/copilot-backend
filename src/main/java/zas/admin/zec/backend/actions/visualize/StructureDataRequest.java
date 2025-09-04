package zas.admin.zec.backend.actions.visualize;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public record StructureDataRequest(MultipartFile file, List<String> fields) {
}