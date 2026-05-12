package com.unique.zhangaizerocode.core.saver;

import com.unique.zhangaizerocode.ai.model.HtmlCodeResult;
import com.unique.zhangaizerocode.ai.model.MultiFileCodeResult;
import com.unique.zhangaizerocode.core.CodeFileSaver;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;

import java.io.File;

public class CodeFileSaverExecutor {

    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();

    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult);
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult)codeResult);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码类型" + codeGenType);

        };
    }
}
