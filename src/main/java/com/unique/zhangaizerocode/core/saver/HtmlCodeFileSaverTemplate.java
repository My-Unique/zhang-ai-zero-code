package com.unique.zhangaizerocode.core.saver;

import cn.hutool.core.util.StrUtil;
import com.unique.zhangaizerocode.ai.model.HtmlCodeResult;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;


public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {
    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"HTML代码不能为空");
        }

    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath,"index.html",result.getHtmlCode());
    }



}
