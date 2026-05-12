package com.unique.zhangaizerocode.core.saver;

import cn.hutool.core.util.StrUtil;
import com.unique.zhangaizerocode.ai.model.MultiFileCodeResult;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;

public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult>{


    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"HTML代码不能为空");
        }
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {

        writeToFile(baseDirPath,"index.html",result.getHtmlCode());

        writeToFile(baseDirPath,"style.css",result.getCssCode());

        writeToFile(baseDirPath,"script.js" ,result.getJsCode());

    }
}
