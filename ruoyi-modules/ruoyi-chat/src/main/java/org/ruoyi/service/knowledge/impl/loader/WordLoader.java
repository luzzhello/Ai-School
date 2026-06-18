package org.ruoyi.service.knowledge.impl.loader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.ruoyi.service.knowledge.ResourceLoader;
import org.ruoyi.service.knowledge.TextSplitter;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class WordLoader implements ResourceLoader {
    private final TextSplitter textSplitter;

    @Override
    public String getContent(InputStream inputStream) {
        try (BufferedInputStream in = inputStream instanceof BufferedInputStream buffered
            ? buffered : new BufferedInputStream(inputStream)) {
            in.mark(8);
            if (isOle2Doc(in)) {
                in.reset();
                try (HWPFDocument document = new HWPFDocument(in);
                     WordExtractor extractor = new WordExtractor(document)) {
                    return extractor.getText();
                }
            }
            in.reset();
            try (XWPFDocument document = new XWPFDocument(in);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isOle2Doc(InputStream in) throws IOException {
        byte[] header = new byte[8];
        int read = in.read(header);
        if (read < 8) {
            return false;
        }
        return header[0] == (byte) 0xD0 && header[1] == (byte) 0xCF
            && header[2] == 0x11 && header[3] == (byte) 0xE0;
    }

    @Override
    public List<String> getChunkList(String content, String kid) {
        return textSplitter.split(content, kid);
    }

}
