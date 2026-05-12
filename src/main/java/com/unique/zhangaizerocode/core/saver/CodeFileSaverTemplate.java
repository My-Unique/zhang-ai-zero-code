package com.unique.zhangaizerocode.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import opennlp.tools.util.StringUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;


public abstract class CodeFileSaverTemplate<T> {
   protected static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

   //保存代码的完整流程
   public final File saveCode(T result) {
      //create_table.sql、验证输入
      validateInput(result);
      //2、构建唯一目录 利用雪花算法
      String baseDirdir = buildUniqueDir();
      //3、保存文件
      saveFiles(result, baseDirdir);
      //4、返回目录文件对象
      return new File(baseDirdir);
   }




   protected void validateInput(T result) {
      if (result == null) {
         throw new BusinessException(ErrorCode.SYSTEM_ERROR,"代码对象不能为空");
      }
   }


   protected  String buildUniqueDir() {
      //首先获得代码的结果类型
      String codeType = getCodeType().getValue();
      //然后构建唯一目录
      String uniqueDirName = StrUtil.format("{}_{}", codeType, IdUtil.getSnowflakeNextIdStr());
      //拼接目录
      String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
      //创建这个实际的目录
      FileUtil.mkdir(dirPath);
      // 返回目录的名字
      return dirPath;
   }

   protected abstract CodeGenTypeEnum getCodeType();

   protected static void writeToFile(String dirPath, String filename, String content) {
      if (StrUtil.isNotBlank(content)) {
         String filePath = dirPath + File.separator + filename;
         FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
      }
   }

   protected abstract void saveFiles(T result, String baseDirPath);


}
