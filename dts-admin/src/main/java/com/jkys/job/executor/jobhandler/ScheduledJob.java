package com.jkys.job.executor.jobhandler;

import com.jkys.job.core.biz.model.ReturnT;
import com.jkys.job.core.handler.IJobHandler;
import com.jkys.job.core.handler.annotation.XxlJob;
import com.jkys.job.core.log.XxlJobLogger;
import com.jkys.job.core.util.ShardingUtil;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.logging.KettleLogStore;
import org.pentaho.di.core.logging.LogLevel;
import org.pentaho.di.core.logging.LoggingBuffer;
import org.pentaho.di.job.Job;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * XxlJob开发示例（Bean模式）
 *
 * 开发步骤：
 * 1、在Spring Bean实例中，开发Job方法，方式格式要求为 "public ReturnT<String> execute(String param)"
 * 2、为Job方法添加注解 "@XxlJob(value="自定义jobhandler名称", init = "JobHandler初始化方法", destroy = "JobHandler销毁方法")"，注解value值对应的是调度中心新建任务的JobHandler属性的值。
 * 3、执行日志：需要通过 "XxlJobLogger.log" 打印执行日志；
 *
 * @author xuxueli 2019-12-11 21:52:51
 */
@Component
public class ScheduledJob {
    private static Logger logger = LoggerFactory.getLogger(ScheduledJob.class);

    private static final String PDI_PLUGIN_HOME="PDI_PLUGINS_HOME";

    @PostConstruct
    public void initKettle() {
        String pdiPluginPath = System.getenv(PDI_PLUGIN_HOME);
        if (StringUtils.isEmpty(pdiPluginPath)) {
            String dir = System.getProperty("user.dir");
            String pluginPath = dir + File.separator + "BOOT-INF" + File.separator + "classes" + File.separator + "plugins";
            File pluginFile = new File(pluginPath);
            if (pluginFile.exists()) {
                pdiPluginPath = pluginPath;
            }

        }
        if (StringUtils.isNotEmpty(pdiPluginPath)) {
            System.setProperty(Const.PLUGIN_BASE_FOLDERS_PROP,pdiPluginPath);
        }
        try {
            KettleEnvironment.init();
        } catch (KettleException e) {
            e.printStackTrace();
        }
    }


    /**
     * 1、简单任务示例（Bean模式）
     */
    @XxlJob("demoJobHandler")
    public ReturnT<String> demoJobHandler(String param) throws Exception {
        XxlJobLogger.log("XXL-JOB, Hello World.");

        for (int i = 0; i < 5; i++) {
            XxlJobLogger.log("beat at:" + i);
            TimeUnit.SECONDS.sleep(2);
        }
        return ReturnT.SUCCESS;
    }


    /**
     * 2、分片广播任务
     */
    @XxlJob("shardingJobHandler")
    public ReturnT<String> shardingJobHandler(String param) throws Exception {

        // 分片参数
        ShardingUtil.ShardingVO shardingVO = ShardingUtil.getShardingVo();
        XxlJobLogger.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardingVO.getIndex(), shardingVO.getTotal());

        // 业务逻辑
        for (int i = 0; i < shardingVO.getTotal(); i++) {
            if (i == shardingVO.getIndex()) {
                XxlJobLogger.log("第 {} 片, 命中分片开始处理", i);
            } else {
                XxlJobLogger.log("第 {} 片, 忽略", i);
            }
        }

        return ReturnT.SUCCESS;
    }


    /**
     * 3、命令行任务
     */
    @XxlJob("commandJobHandler")
    public ReturnT<String> commandJobHandler(String param) throws Exception {
        String command = param;
        int exitValue = -1;

        BufferedReader bufferedReader = null;
        try {
            // command process
            Process process = Runtime.getRuntime().exec(command);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(process.getInputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(bufferedInputStream));

            // command log
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                XxlJobLogger.log(line);
            }

            // command exit
            process.waitFor();
            exitValue = process.exitValue();
        } catch (Exception e) {
            XxlJobLogger.log(e);
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }

        if (exitValue == 0) {
            return IJobHandler.SUCCESS;
        } else {
            return new ReturnT<String>(IJobHandler.FAIL.getCode(), "command exit value("+exitValue+") is failed");
        }
    }


    /**
     * 4、跨平台Http任务
     */
    @XxlJob("httpJobHandler")
    public ReturnT<String> httpJobHandler(String param) throws Exception {

        // request
        HttpURLConnection connection = null;
        BufferedReader bufferedReader = null;
        try {
            // connection
            URL realUrl = new URL(param);
            connection = (HttpURLConnection) realUrl.openConnection();

            // connection setting
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setReadTimeout(5 * 1000);
            connection.setConnectTimeout(3 * 1000);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept-Charset", "application/json;charset=UTF-8");

            // do connection
            connection.connect();

            //Map<String, List<String>> map = connection.getHeaderFields();

            // valid StatusCode
            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                throw new RuntimeException("Http Request StatusCode(" + statusCode + ") Invalid.");
            }

            // result
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            String responseMsg = result.toString();

            XxlJobLogger.log(responseMsg);
            return ReturnT.SUCCESS;
        } catch (Exception e) {
            XxlJobLogger.log(e);
            return ReturnT.FAIL;
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e2) {
                XxlJobLogger.log(e2);
            }
        }

    }

    /**
     * 5、生命周期任务示例：任务初始化与销毁时，支持自定义相关逻辑；
     */
    @XxlJob(value = "demoJobHandler2", init = "init", destroy = "destroy")
    public ReturnT<String> demoJobHandler2(String param) throws Exception {
        XxlJobLogger.log("XXL-JOB, Hello World.");
        return ReturnT.SUCCESS;
    }
    public void init(){
        logger.info("init");
    }
    public void destroy(){
        logger.info("destory");
    }

    @XxlJob("pdiTransHandler")
    public ReturnT<String> pdiTransHandler(String param) throws Exception {

        Trans trans = runTransformationFromFileSystem(param);
        if (trans != null) {
            LoggingBuffer appender = KettleLogStore.getAppender();
            String logText = appender.getBuffer( trans.getLogChannelId(), false ).toString();
            XxlJobLogger.log(logText);
        }
        return ReturnT.SUCCESS;
    }

    @XxlJob("pdiJobHandler")
    public ReturnT<String> pdiJobHandler(String param) throws Exception {

        Job job = runJobFromFileSystem(param);
        if (job != null) {
            LoggingBuffer appender = KettleLogStore.getAppender();
            String logText = appender.getBuffer( job.getLogChannelId(), false ).toString();
            XxlJobLogger.log(logText);
        }
        return ReturnT.SUCCESS;
    }

    public Trans runTransformationFromFileSystem(String param ) {
        if (StringUtils.isEmpty(param)) {
            return null;
        }
        LogLevel logLevel = LogLevel.BASIC;
        String filename = param;
        Map<String, String> transParamsMap = new HashMap<>();
        String[] params = param.split(",");
        if (params.length > 1) {
            filename = params[0];
            for (int i = 1; i < params.length; i++) {
                if (StringUtils.isNotEmpty(params[i]) && params[i].contains("=")) {
                    String[] kv = params[i].split("=");
                    if ("/param:level".equalsIgnoreCase(kv[0])) {
                        logLevel = LogLevel.getLogLevelForCode(kv[1]);
                    } else {
                        transParamsMap.put(kv[0],kv[1]);
                    }
                }
            }
        }

        try {
            XxlJobLogger.log( "***************************************************************************************" );
            XxlJobLogger.log( "Attempting to run transformation " + filename + " from file system" );
            XxlJobLogger.log( "***************************************************************************************\n" );
            // Loading the transformation file from file system into the TransMeta object.
            // The TransMeta object is the programmatic representation of a transformation definition.
            TransMeta transMeta = new TransMeta( filename, (Repository) null );

            // The next section reports on the declared parameters and sets them to arbitrary values
            // for demonstration purposes
            XxlJobLogger.log( "Attempting to read and set named parameters" );
            String[] declaredParameters = transMeta.listParameters();
            for ( int i = 0; i < declaredParameters.length; i++ ) {
                String parameterName = declaredParameters[i];

                // determine the parameter description and default values for display purposes
                String description = transMeta.getParameterDescription( parameterName );
                String defaultValue = transMeta.getParameterDefault( parameterName );

                if (transParamsMap.containsKey(parameterName)) {
                    String output = String.format( "Setting parameter %s to \"%s\" [description: \"%s\", default: \"%s\"]",
                            parameterName, transParamsMap.get(parameterName), description, defaultValue );
                    XxlJobLogger.log( output );

                    // assign the value to the parameter on the transformation
                    transMeta.setParameterValue( parameterName, transParamsMap.get(parameterName) );
                    transParamsMap.remove(parameterName);
                }
            }


            // set variables to transmeta
            if (transParamsMap.size() > 0) {
                for (Map.Entry<String, String> entry : transParamsMap.entrySet()) {
                    transMeta.setVariable(entry.getKey(), entry.getValue());
                }

            }

            // Creating a transformation object which is the programmatic representation of a transformation
            // A transformation object can be executed, report success, etc.
            Trans transformation = new Trans( transMeta );

            // adjust the log level
            transformation.setLogLevel( logLevel );

            XxlJobLogger.log( "\nStarting transformation" );

            // starting the transformation, which will execute asynchronously
            transformation.execute( new String[0] );

            // waiting for the transformation to finish
            transformation.waitUntilFinished();

            // retrieve the result object, which captures the success of the transformation
            Result result = transformation.getResult();

            // report on the outcome of the transformation
            String outcome = String.format( "\nTrans %s executed %s", filename,
                    ( result.getNrErrors() == 0 ? "successfully" : "with " + result.getNrErrors() + " errors" ) );
            XxlJobLogger.log( outcome );

            return transformation;
        } catch ( Exception e ) {

            // something went wrong, just log and return
            e.printStackTrace();
            return null;
        }
    }

    public Job runJobFromFileSystem(String param ) {

        LogLevel logLevel = LogLevel.BASIC;

        if (StringUtils.isEmpty(param)) {
            return null;
        }
        String filename = param;
        Map<String, String> jobParamsMap = new HashMap<>();
        String[] params = param.split(",");
        if (params.length > 1) {
            filename = params[0];
            for (int i = 1; i < params.length; i++) {
                if (StringUtils.isNotEmpty(params[i]) && params[i].contains("=")) {
                    String[] kv = params[i].split("=");
                    if ("/param:level".equalsIgnoreCase(kv[0])) {
                        logLevel = LogLevel.getLogLevelForCode(kv[1]);
                    } else {
                        jobParamsMap.put(kv[0],kv[1]);
                    }

                }
            }
        }
        try {
            XxlJobLogger.log( "***************************************************************************************" );
            XxlJobLogger.log( "Attempting to run job " + filename + " from file system" );
            XxlJobLogger.log( "***************************************************************************************\n" );
            // Loading the job file from file system into the JobMeta object.
            // The JobMeta object is the programmatic representation of a job
            // definition.
            JobMeta jobMeta = new JobMeta( filename, null );

            // The next section reports on the declared parameters and sets them
            // to arbitrary values
            // for demonstration purposes
            XxlJobLogger.log( "Attempting to read and set named parameters" );
            String[] declaredParameters = jobMeta.listParameters();
            for ( int i = 0; i < declaredParameters.length; i++ ) {
                String parameterName = declaredParameters[i];

                // determine the parameter description and default values for
                // display purposes
                String description = jobMeta.getParameterDescription( parameterName );
                String defaultValue = jobMeta.getParameterDefault( parameterName );

                if (jobParamsMap.containsKey(parameterName)) {
                    // set the parameter value
                    String parameterValue = jobParamsMap.get(parameterName);

                    String output = String.format( "Setting parameter %s to \"%s\" [description: \"%s\", default: \"%s\"]",
                            parameterName, parameterValue, description, defaultValue );
                    XxlJobLogger.log( output );

                    // assign the value to the parameter on the job
                    jobMeta.setParameterValue( parameterName, parameterValue );
                    jobParamsMap.remove(parameterName);
                }

            }
            if (jobParamsMap.size() > 0) {
                for (Map.Entry<String, String> entry : jobParamsMap.entrySet()) {
                    jobMeta.setVariable(entry.getKey(), entry.getValue());
                }

            }

            // Creating a Job object which is the programmatic representation of
            // a job
            // A Job object can be executed, report success, etc.
            Job job = new Job( null, jobMeta );

            // adjust the log level
            job.setLogLevel( logLevel );

            XxlJobLogger.log( "\nStarting job" );

            // starting the job thread, which will execute asynchronously
            job.start();

            // waiting for the job to finish
            job.waitUntilFinished();

            // retrieve the result object, which captures the success of the job
            Result result = job.getResult();

            // report on the outcome of the job
            String outcome = String.format( "\nJob %s executed with result: %s and %d errors\n",
                    filename, result.getResult(), result.getNrErrors() );
            XxlJobLogger.log( outcome );

            return job;
        } catch ( Exception e ) {
            // something went wrong, just log and return
            e.printStackTrace();
            return null;
        }
    }


}
