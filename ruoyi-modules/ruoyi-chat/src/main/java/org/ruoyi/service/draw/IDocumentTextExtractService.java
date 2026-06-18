package org.ruoyi.service.draw;

import org.ruoyi.domain.vo.draw.DocumentParseResultVo;
import org.springframework.web.multipart.MultipartFile;

public interface IDocumentTextExtractService {

    DocumentParseResultVo parse(MultipartFile file);
}
