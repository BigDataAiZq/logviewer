package com.zjh.logviewer.action;

import com.zjh.logviewer.ServerCfg;
import com.zjh.logviewer.SysParam;
import com.zjh.logviewer.model.FileAttri;
import com.zjh.logviewer.model.Server;
import com.zjh.logviewer.ssh.ClientFactory;
import com.zjh.logviewer.ssh.SFTPClient;
import org.jflame.commons.codec.TranscodeHelper;
import org.jflame.commons.exception.RemoteAccessException;
import org.jflame.commons.model.CallResult;
import org.jflame.commons.model.CallResult.ResultEnum;
import org.jflame.commons.util.CharsetHelper;
import org.jflame.commons.util.CollectionHelper;
import org.jflame.commons.util.StringHelper;
import org.jflame.commons.util.file.FileHelper;
import org.jflame.commons.util.file.ZipHelper;
import org.jflame.web.WebUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

// @WebServlet("/show.do")
public class ShowServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    ExecutorService executorService;

    public ShowServlet() {
        executorService = Executors.newFixedThreadPool(2, new BasicThreadFactory.Builder().daemon(true)
                .build());
    }

    @Override
    public void destroy() {
        executorService.shutdown();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String cmd = request.getParameter("obj");
        if (("server".equals(cmd))) {
            CallResult<List<Server>> result = CallResult.ok(ServerCfg.getServers());
            WebUtils.outJson(response, result);
        } else if ("down".equals(cmd)) {
            // CallResult<Object> result = new CallResult<>();
            downloadLog(request, response);
        } else if ("view".equals(cmd)) {
            viewLog(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String cmd = request.getParameter("obj");
        CallResult<Object> result = new CallResult<>(ResultEnum.FAILED);
        if ("file".equals(cmd)) {
            lsFiles(request, response, result);
        }
        WebUtils.outJson(response, result);
    }

    /**
     * 列出日志文件目录树
     * 
     * @param request
     * @param response
     * @param result
     * @throws IOException
     */
    private void lsFiles(HttpServletRequest request, HttpServletResponse response, CallResult<Object> result)
            throws IOException {
        List<FileAttri> fileAttris = new ArrayList<>();
        String ip = request.getParameter("ip");
        String parentId = request.getParameter("id");
        Optional<Server> selectedServer = ServerCfg.getServer(ip);
        if (!selectedServer.isPresent()) {
            result.paramError(ip + "服务器未配置");
            return;
        }

        Server connServer = selectedServer.get();
        boolean isNeedSaveCfg = false;
        if (StringHelper.isEmpty(selectedServer.get()
                .getUser()) || StringHelper.isEmpty(
                        selectedServer.get()
                                .getPwd())) {
            String tmpSu = request.getParameter("su");
            if (StringHelper.isEmpty(tmpSu)) {
                result.status(4001)
                        .message("请输入远程连接用户和密码");
                return;
            }
            String namePwdBase64 = CharsetHelper.getUtf8String(TranscodeHelper.dencodeBase64(tmpSu));
            namePwdBase64 = StringUtils.reverse(namePwdBase64);
            String[] tmpNps = namePwdBase64.split("&");
            String user = tmpNps[0];
            String password = tmpNps[1];
            if (StringHelper.isEmpty(tmpSu) || StringHelper.isEmpty(password)) {
                result.status(4001)
                        .message("请输入远程连接用户和密码");
                return;
            } else {
                connServer.setUser(user);
                connServer.setPwd(password);
                isNeedSaveCfg = true;
            }
        }

        String[] dirs = null;
        String[] excludes = connServer.excludeLogs();
        if (StringHelper.isNotEmpty(parentId)) {
            String parentDir = TranscodeHelper.dencodeHexString(parentId);
            if (!connServer.isCanRead(parentDir)) {
                result.error("不可访问文件");
                return;
            }
            try {
                SFTPClient client = ClientFactory.getFtpClient(request.getSession(false)
                        .getId(), connServer);
                List<FileAttri> lst = client.ls(parentDir, false, excludes);
                if (CollectionHelper.isNotEmpty(lst)) {
                    for (FileAttri fa : lst) {
                        fa.setId(TranscodeHelper.encodeHexString(fa.getPath()));
                        if (fa.isDir()) {
                            fa.setState("closed");
                        }
                        fileAttris.add(fa);
                    }
                }
            } catch (RemoteAccessException e) {
                if (e.getStatusCode() == 4001) {
                    connServer.setUser(null);
                    connServer.setPwd(null);
                    result.status(4001)
                            .message(e.getMessage());
                } else {
                    result.error(e.getMessage());
                }
            }
        } else {
            // 根目录开始
            dirs = connServer.dirs();
            for (String d : dirs) {
                FileAttri fa = new FileAttri();
                fa.setPath(d);
                fa.setNodeName(d);
                fa.setId(TranscodeHelper.encodeHexString(d));
                fa.setState("closed");
                fa.setDir(true);
                fileAttris.add(fa);
            }
        }

        result.setResult(ResultEnum.SUCCESS);
        result.setData(fileAttris);
        if (isNeedSaveCfg) {
            ServerCfg.save();
        }
    }

    private void downloadLog(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String downFile = request.getParameter("f");
        if (StringHelper.isEmpty(downFile)) {
            response.getWriter()
                    .print("请选择要下载的文件");
            return;
        }
        Optional<Server> selectedServer = ServerCfg.getServer(request.getParameter("ip"));
        if (!selectedServer.isPresent()) {
            response.getWriter()
                    .print("服务器未配置");
            return;
        }

        downFile = TranscodeHelper.dencodeHexString(downFile);
        if (!"log".equals(FileHelper.getExtension(downFile, false))) {
            response.getWriter()
                    .print("不允许下载的文件类型");
            return;
        }
        Server connServer = selectedServer.get();

        boolean baseDirOk = connServer.isCanRead(downFile);
        if (!baseDirOk) {
            response.getWriter()
                    .print("文件路径不正确");
            return;
        }
        ServletOutputStream output = null;
        Path dstFilePath = null;
        try {
            String downFileDir = FileHelper.getDir(downFile);
            Path downFileDirPath = Paths.get(SysParam.tmpDir()
                    .toString(), connServer.getIp(), downFileDir);
            Files.createDirectories(downFileDirPath);

            String downFilename = FileHelper.getFilename(downFile);
            String dstFileFullName = downFileDirPath.resolve(downFilename)
                    .toString();

            SFTPClient client = ClientFactory.getFtpClient(request.getSession(false)
                    .getId(), connServer);

            client.download(downFile, dstFileFullName);

            dstFilePath = Paths.get(dstFileFullName);
            if (!Files.exists(dstFilePath)) {
                response.getWriter()
                        .print("文件下载失败");
                return;
            }
            // 大于1M压缩
            long fileSize = Files.size(dstFilePath);
            if (fileSize > 1048576) {
                String zipFile = ZipHelper.zip(dstFileFullName, downFileDirPath.toString(), 3);
                dstFilePath = Paths.get(zipFile);
                fileSize = Files.size(dstFilePath);
                downFilename = dstFilePath.getFileName()
                        .toString();
            }
            log("下载文件:" + dstFilePath + " 大小:" + fileSize);
            response.reset();
            WebUtils.setFileDownloadHeader(response, downFilename, fileSize);

            byte[] buf = new byte[1024];
            int len = 0;
            try (BufferedInputStream br = new BufferedInputStream(Files.newInputStream(dstFilePath))) {
                output = response.getOutputStream();
                while ((len = br.read(buf)) > 0)
                    output.write(buf, 0, len);
            }
            output.flush();

        } catch (Exception e) {
            throw new ServletException(e);
        } finally {
            /*if (dstFilePath != null) {
                executorService.execute(new DeleteFileThread(dstFilePath));
            }*/
        }
    }

    private void viewLog(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String downFile = request.getParameter("f");
        PrintWriter out = response.getWriter();
        if (StringHelper.isEmpty(downFile)) {
            out.print("请选择要下载的文件");
            return;
        }
        Optional<Server> selectedServer = ServerCfg.getServer(request.getParameter("ip"));
        if (!selectedServer.isPresent()) {
            out.print("服务器未配置");
            return;
        }

        downFile = TranscodeHelper.dencodeHexString(downFile);
        if (!"log".equals(FileHelper.getExtension(downFile, false))) {
            out.print("不允许查看的文件类型");
            return;
        }
        Server connServer = selectedServer.get();

        boolean baseDirOk = connServer.isCanRead(downFile);
        if (!baseDirOk) {
            out.print("文件路径不正确");
            return;
        }
        try {
            SFTPClient client = ClientFactory.getFtpClient(request.getSession(false)
                    .getId(), connServer);
            String fileText = client.getFileContent(downFile, StandardCharsets.UTF_8);

            response.setContentType("text/plain");
            out.println(fileText);
            out.flush();

        } catch (RemoteAccessException e) {
            throw new ServletException(e);
        }
    }

    private class DeleteFileThread implements Runnable {

        private Path deleteFile;

        public DeleteFileThread(Path _deleteFile) {
            deleteFile = _deleteFile;
        }

        @Override
        public void run() {
            try {
                Files.deleteIfExists(deleteFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
