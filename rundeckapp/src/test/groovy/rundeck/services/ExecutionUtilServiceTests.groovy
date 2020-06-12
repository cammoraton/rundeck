/*
 * Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rundeck.services

import com.dtolabs.rundeck.core.execution.ServiceThreadBase
import com.dtolabs.rundeck.core.execution.StepExecutionItem
import com.dtolabs.rundeck.core.execution.workflow.ControlBehavior
import com.dtolabs.rundeck.core.execution.workflow.WFSharedContext
import com.dtolabs.rundeck.core.execution.workflow.WorkflowExecutionItem
import com.dtolabs.rundeck.core.execution.workflow.WorkflowExecutionResult
import com.dtolabs.rundeck.core.execution.workflow.steps.StepExecutionResult
import com.dtolabs.rundeck.core.execution.workflow.steps.node.impl.ExecCommandExecutionItem
import com.dtolabs.rundeck.core.execution.workflow.steps.node.impl.ScriptFileCommandExecutionItem
import com.dtolabs.rundeck.core.execution.workflow.steps.node.impl.ScriptURLCommandExecutionItem
import com.dtolabs.rundeck.core.utils.ThreadBoundOutputStream
import com.dtolabs.rundeck.execution.JobExecutionItem
import com.dtolabs.rundeck.execution.JobRefCommand
import grails.test.hibernate.HibernateSpec
import grails.testing.services.ServiceUnitTest
import groovy.mock.interceptor.MockFor
import org.grails.plugins.metricsweb.MetricService
import rundeck.CommandExec
import rundeck.JobExec
import rundeck.Workflow
import rundeck.Execution
import rundeck.services.logging.ExecutionLogWriter

import static org.junit.Assert.*

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
class ExecutionUtilServiceTests extends HibernateSpec implements ServiceUnitTest<ExecutionUtilService>{

    List<Class> getDomainClasses() { [Execution, CommandExec, JobExec, Workflow] }

    void testfinishExecutionMetricsSuccess() {
        when:
        def executionUtilService = new ExecutionUtilService()
        def control=new MockFor(MetricService)
        control.demand.markMeter(1..1){String clazz, String key->
            assertEquals('rundeck.services.ExecutionService',clazz)
            assertEquals('executionSuccessMeter',key)
        }
        executionUtilService.metricService=control.proxyInstance()
        def thread=new ServiceThreadBase()
        thread.success=true
        executionUtilService.finishExecutionMetrics([thread:thread])

        then:
        // above asserts have validation
        1 == 1
    }

    void testfinishExecutionMetricsFailure() {
        when:
        def executionUtilService = new ExecutionUtilService()
        def control=new MockFor(MetricService)
        control.demand.markMeter(1..1){String clazz, String key->
            assertEquals('rundeck.services.ExecutionService',clazz)
            assertEquals('executionFailureMeter',key)
        }
        executionUtilService.metricService=control.proxyInstance()
        def thread=new ServiceThreadBase()
        thread.success=false
        executionUtilService.finishExecutionMetrics([thread:thread])

        then:
        // above asserts have validation
        1 == 1
    }

    /**
     * Finish logging when no error cause
     */
    void testFinishExecutionLoggingNoMessage(){

        when:
        def executionUtilService = new ExecutionUtilService()
        def thread = new ServiceThreadBase<WorkflowExecutionResult>()
        thread.success = false
        def logcontrol = new MockFor(ExecutionLogWriter)
        logcontrol.demand.logError(1..1){String value->
            assertEquals("Execution failed: 1 in project p1: null",value)
        }
        logcontrol.demand.close(1..1){->
        }
        def loghandler=logcontrol.proxyInstance()

        executionUtilService.grailsApplication =  [config:[rundeck:[execution:[logs:[fileStorage:[generateExecutionXml:false]]]]]]
        executionUtilService.sysThreadBoundOut=new MockForThreadOutputStream(null)
        executionUtilService.sysThreadBoundErr=new MockForThreadOutputStream(null)

        executionUtilService.finishExecutionLogging([thread: thread,loghandler: loghandler,execution:[id:1, project:'p1']])

        then:
        // above asserts have validation
        1 == 1
    }

    /**
     * Finish logging when no error cause, generating execution xml
     */
    def testFinishExecutionLoggingNoMessageGenerateExecutionXml(){
        given:
        Execution e = new Execution(argString: "-test args",
                user: "testuser", project: "p1", loglevel: 'WARN',
                doNodedispatch: false,
                workflow: new Workflow(commands: [new CommandExec(adhocExecution: true, adhocRemoteString: 'a remote string')]).save())
        assertNotNull(e.save())

        def executionUtilService = new ExecutionUtilService()
        def thread = new ServiceThreadBase<WorkflowExecutionResult>()
        thread.success = false

        def logFileStorageServiceMock = new MockFor(LogFileStorageService)
        logFileStorageServiceMock.demand.getFileForExecutionFiletype(1..1){
            Execution e2, String filetype, boolean stored ->
                assertEquals(1, e2.id)
                assertEquals(ProjectService.EXECUTION_XML_LOG_FILETYPE, filetype)
                assertEquals(false, stored)
                return File.createTempFile("${e.id}.execution", ".xml")
        }

        executionUtilService.logFileStorageService = logFileStorageServiceMock.proxyInstance()

        def logcontrol = new MockFor(ExecutionLogWriter)
        logcontrol.demand.logError(1..1){String value->
            assertEquals("Execution failed: 1 in project p1: null",value)
        }
        logcontrol.demand.close(1..1){->
        }
        def loghandler=logcontrol.proxyInstance()

        executionUtilService.grailsApplication =  [config:[rundeck:[execution:[logs:[fileStorage:[generateExecutionXml:true]]]]]]

        executionUtilService.sysThreadBoundOut=new MockForThreadOutputStream(null)
        executionUtilService.sysThreadBoundErr=new MockForThreadOutputStream(null)
        when:
        executionUtilService.finishExecutionLogging([thread: thread,loghandler: loghandler,execution:e])
        then:
            true
    }

    /**
     * Finish logging when no error cause, with result
     */
    void testFinishExecutionLoggingNoMessageWithResult(){
        when:
        def executionUtilService = new ExecutionUtilService()
        def thread = new ServiceThreadBase<WorkflowExecutionResult>()
        thread.success = false
        thread.resultObject=new WorkflowExecutionResult(){
            @Override
            String getStatusString() {
                return null
            }

            @Override
            ControlBehavior getControlBehavior() {
                return null
            }

            @Override
            boolean isSuccess() {
                return false
            }

            @Override
            Throwable getException() {
                return null
            }

            @Override
            List<StepExecutionResult> getResultSet() {
                return null
            }

            @Override
            Map<String, Collection<StepExecutionResult>> getNodeFailures() {
                return null
            }

            @Override
            Map<Integer, StepExecutionResult> getStepFailures() {
                return null
            }

            @Override
            String toString() {
                "abcd"
            }

            @Override
            WFSharedContext getSharedContext() {
                return null
            }
        }

        def logcontrol = new MockFor(ExecutionLogWriter)
        logcontrol.demand.logVerbose(1..1){String value->
            assertEquals("abcd",value)
        }
        logcontrol.demand.logError(1..1){String value->
            assertEquals("Execution failed: 1 in project x1: abcd",value)
        }
        logcontrol.demand.close(1..1){->
        }
        def loghandler=logcontrol.proxyInstance()
        executionUtilService.grailsApplication =  [config:[rundeck:[execution:[logs:[fileStorage:[generateExecutionXml:false]]]]]]

        executionUtilService.sysThreadBoundOut=new MockForThreadOutputStream(null)
        executionUtilService.sysThreadBoundErr=new MockForThreadOutputStream(null)

        executionUtilService.finishExecutionLogging([thread: thread,loghandler: loghandler,execution:[id:1, project:"x1"]])

        then:
        // above asserts have validation
        1 == 1
    }
    /**
     * Finish logging when no error cause, with result
     */
    void testFinishExecutionLoggingCausedByException(){
        when:
        def executionUtilService = new ExecutionUtilService()
        def thread = new ServiceThreadBase<WorkflowExecutionResult>()
        thread.success = false
        thread.resultObject=null
        thread.thrown=new Exception("exceptionTest1")
        def logcontrol = new MockFor(ExecutionLogWriter)
        logcontrol.demand.logError(1..1){String value->
            assertEquals("exceptionTest1",value)
        }
        logcontrol.demand.logVerbose(1..1){String value->
            assertNotNull(value)
            assertTrue(value.contains("exceptionTest1"))
        }
        logcontrol.demand.close(1..1){->
        }
        def loghandler=logcontrol.proxyInstance()
        executionUtilService.grailsApplication =  [config:[rundeck:[execution:[logs:[fileStorage:[generateExecutionXml:false]]]]]]
        executionUtilService.sysThreadBoundOut=new MockForThreadOutputStream(null)
        executionUtilService.sysThreadBoundErr=new MockForThreadOutputStream(null)

        executionUtilService.finishExecutionLogging([thread: thread,loghandler: loghandler,execution:[id:1]])

        then:
        // above asserts have validation
        1 == 1
    }
    /**
     * Finish logging when no error cause, with result
     */
    void testFinishExecutionLoggingCausedByExceptionWithCause(){
        when:
        def executionUtilService = new ExecutionUtilService()
        def thread = new ServiceThreadBase<WorkflowExecutionResult>()
        thread.success = false
        thread.resultObject=null
        def cause=new Exception("exceptionCause1")
        thread.thrown=new Exception("exceptionTest1",cause)
        def logcontrol = new MockFor(ExecutionLogWriter)
        logcontrol.demand.logError(1..1){String value->
            assertEquals("exceptionTest1,Caused by: exceptionCause1",value)
        }
        logcontrol.demand.logVerbose(1..1){String value->
            assertNotNull(value)
            assertTrue(value.contains("exceptionTest1"))
        }
        logcontrol.demand.close(1..1){-> }
        def loghandler=logcontrol.proxyInstance()
        executionUtilService.grailsApplication =  [config:[rundeck:[execution:[logs:[fileStorage:[generateExecutionXml:false]]]]]]
        executionUtilService.sysThreadBoundOut=new MockForThreadOutputStream(null)
        executionUtilService.sysThreadBoundErr=new MockForThreadOutputStream(null)

        executionUtilService.finishExecutionLogging([thread: thread,loghandler: loghandler,execution:[id:1]])

        then:
        // above asserts have validation
        1 == 1
    }


    void testItemForWFCmdItem_command(){
        when:
        def testService = service
        //exec
        CommandExec ce = new CommandExec(adhocRemoteString: 'exec command')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ExecCommandExecutionItem)
        ExecCommandExecutionItem item=(ExecCommandExecutionItem) res
        assertEquals(['exec','command'],item.command as List)

        then:
        // above asserts have validation
        1 == 1
    }

    void testItemForWFCmdItem_script() {
        when:
        def testService = service
        //adhoc local string
        CommandExec ce = new CommandExec(adhocLocalString: 'local script')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptFileCommandExecutionItem)
        ScriptFileCommandExecutionItem item=(ScriptFileCommandExecutionItem) res

        then:
        assertEquals('local script',item.script)
        assertNull(item.scriptAsStream)
        assertNull(item.serverScriptFilePath)
        assertNotNull(item.args)
        assertEquals(0,item.args.length)
    }

    void testItemForWFCmdItem_script_fileextension() {
        when:
        def testService = service
        //adhoc local string
        CommandExec ce = new CommandExec(adhocLocalString: 'local script',fileExtension: 'abc')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptFileCommandExecutionItem)
        ScriptFileCommandExecutionItem item=(ScriptFileCommandExecutionItem) res

        then:
        assertEquals('local script',item.script)
        assertNull(item.scriptAsStream)
        assertNull(item.serverScriptFilePath)
        assertNotNull(item.args)
        assertEquals(0,item.args.length)
        assertEquals('abc',item.fileExtension)
    }

    void testItemForWFCmdItem_scriptArgs() {
        when:
        def testService = service
        //adhoc local string, args
        CommandExec ce = new CommandExec(adhocLocalString: 'local script',argString: 'some args')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptFileCommandExecutionItem)
        ScriptFileCommandExecutionItem item=(ScriptFileCommandExecutionItem) res

        then:
        assertEquals('local script',item.script)
        assertNull(item.scriptAsStream)
        assertNull(item.serverScriptFilePath)
        assertNotNull(item.args)
        assertEquals(['some', 'args'], item.args as List)
    }

    void testItemForWFCmdItem_scriptfile() {
        when:
        def testService = service
        //adhoc file path
        CommandExec ce = new CommandExec(adhocFilepath: '/some/path', argString: 'some args')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptFileCommandExecutionItem)
        ScriptFileCommandExecutionItem item = (ScriptFileCommandExecutionItem) res

        then:
        assertEquals('/some/path', item.serverScriptFilePath)
        assertNull(item.scriptAsStream)
        assertNull(item.script)
        assertNotNull(item.args)
        assertEquals(['some', 'args'], item.args as List)
    }
    void testItemForWFCmdItem_scriptfile_fileextension() {
        when:
        def testService = service
        //adhoc file path
        CommandExec ce = new CommandExec(adhocFilepath: '/some/path', argString: 'some args',fileExtension: 'xyz')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptFileCommandExecutionItem)
        ScriptFileCommandExecutionItem item = (ScriptFileCommandExecutionItem) res

        then:
        assertEquals('/some/path', item.serverScriptFilePath)
        assertNull(item.scriptAsStream)
        assertNull(item.script)
        assertNotNull(item.args)
        assertEquals(['some', 'args'], item.args as List)
        assertEquals('xyz', item.fileExtension)
    }

    void testItemForWFCmdItem_scripturl() {
        when:
        def testService = service
        //http url script path
        CommandExec ce = new CommandExec(adhocFilepath: 'http://example.com/script', argString: 'some args')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptURLCommandExecutionItem)
        ScriptURLCommandExecutionItem item = (ScriptURLCommandExecutionItem) res

        then:
        assertEquals('http://example.com/script', item.URLString)
        assertNotNull(item.args)
        assertEquals(['some', 'args'], item.args as List)
    }
    void testItemForWFCmdItem_scripturl_fileextension() {
        when:
        def testService = service
        //http url script path
        CommandExec ce = new CommandExec(adhocFilepath: 'http://example.com/script', argString: 'some args',fileExtension: 'mdd')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptURLCommandExecutionItem)
        ScriptURLCommandExecutionItem item = (ScriptURLCommandExecutionItem) res

        then:
        assertEquals('http://example.com/script', item.URLString)
        assertNotNull(item.args)
        assertEquals(['some', 'args'], item.args as List)
        assertEquals('mdd', item.fileExtension)
    }

    void testItemForWFCmdItem_scripturl_https() {
        when:
        def testService = service
        //https url script path
        CommandExec ce = new CommandExec(adhocFilepath: 'https://example.com/script', argString: 'some args')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptURLCommandExecutionItem)
        ScriptURLCommandExecutionItem item = (ScriptURLCommandExecutionItem) res

        then:
        assertEquals('https://example.com/script', item.URLString)
        assertNotNull(item.args)
        assertEquals(['some', 'args'], item.args as List)
    }

    void testItemForWFCmdItem_scripturl_file() {
        when:
        def testService = service
        //file url script path
        CommandExec ce = new CommandExec(adhocFilepath: 'file:/some/script')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptURLCommandExecutionItem)
        ScriptURLCommandExecutionItem item = (ScriptURLCommandExecutionItem) res

        then:
        assertEquals('file:/some/script', item.URLString)
        assertNotNull(item.args)
        assertEquals(0, item.args.length)
    }

    void testItemForWFCmdItem_scripturl_file_args() {
        when:
        def testService = service
        //file url script path
        CommandExec ce = new CommandExec(adhocFilepath: 'file:/some/script', argString: 'some args')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertTrue(res instanceof ScriptURLCommandExecutionItem)
        ScriptURLCommandExecutionItem item = (ScriptURLCommandExecutionItem) res

        then:
        assertEquals('file:/some/script', item.URLString)
        assertNotNull(item.args)
        assertEquals(['some', 'args'], item.args as List)
    }

    void testItemForWFCmdItem_jobref() {
        when:
        def testService = service
        //file url script path
        JobExec ce = new JobExec(jobName: 'abc', jobGroup: 'xyz')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertFalse(res instanceof ScriptURLCommandExecutionItem)
        assertTrue(res instanceof JobExecutionItem)
        JobExecutionItem item = (JobExecutionItem) res

        then:
        assertEquals('xyz/abc', item.getJobIdentifier())
        assertNotNull(item.args)
        assertEquals([], item.args as List)
    }

    void testItemForWFCmdItem_jobref_args() {
        when:
        def testService = service
        //file url script path
        JobExec ce = new JobExec(
                jobName: 'abc',
                jobGroup: 'xyz',
                argString: 'abc def',
                nodeFilter: 'def',
                nodeIntersect: true
        )
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertFalse(res instanceof ScriptURLCommandExecutionItem)
        assertTrue(res instanceof JobExecutionItem)
        JobExecutionItem item = (JobExecutionItem) res

        then:
        assertEquals('xyz/abc', item.getJobIdentifier())
        assertNotNull(item.args)
        assertEquals(['abc', 'def'], item.args as List)
        assertEquals('def', item.getNodeFilter())
        assertEquals(true, item.getNodeIntersect())

    }

    void testItemForWFCmdItem_jobref_externalProject() {
        when:
        def testService = service
        //file url script path
        JobExec ce = new JobExec(jobName: 'abc', jobGroup: 'xyz', jobProject: 'anotherProject')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertFalse(res instanceof ScriptURLCommandExecutionItem)
        assertTrue(res instanceof JobRefCommand)
        JobRefCommand item = (JobRefCommand) res

        then:
        assertEquals('xyz/abc', item.getJobIdentifier())
        assertNotNull(item.args)
        assertEquals([], item.args as List)
    }

    void testItemForWFCmdItem_jobref_args_externalProject() {
        when:
        def testService = service
        //file url script path
        JobExec ce = new JobExec(jobName: 'abc', jobGroup: 'xyz', jobProject: 'anotherProject')
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertFalse(res instanceof ScriptURLCommandExecutionItem)
        assertTrue(res instanceof JobRefCommand)
        JobRefCommand item = (JobRefCommand) res

        then:
        assertEquals('xyz/abc', item.getJobIdentifier())
        assertEquals('anotherProject', item.getProject())
        assertNotNull(item.args)
        assertEquals([], item.args as List)
    }

    void testItemForWFCmdItem_jobref_sameproject() {
        when:
        def testService = service
        //file url script path
        JobExec ce = new JobExec(jobName: 'abc', jobGroup: 'xyz', jobProject: 'jobProject')
        def res = testService.itemForWFCmdItem(ce, null,'jobProject')
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertFalse(res instanceof ScriptURLCommandExecutionItem)
        assertTrue(res instanceof JobExecutionItem)
        JobExecutionItem item = (JobExecutionItem) res
        assertEquals('xyz/abc', item.getJobIdentifier())
        assertNotNull(item.args)
        assertEquals([], item.args as List)
        JobRefCommand jrc = (JobRefCommand) res
        jrc.project=='jobProject'

        then:
        // above asserts have validation
        1 == 1
    }

    void testItemForWFCmdItem_jobref_otherproject() {
        when:
        def testService = service
        JobExec ce = new JobExec(jobName: 'abc', jobGroup: 'xyz',jobProject: 'refProject')
        def res = testService.itemForWFCmdItem(ce,null,'jobProject')
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertFalse(res instanceof ScriptURLCommandExecutionItem)
        assertTrue(res instanceof JobExecutionItem)
        JobExecutionItem item = (JobExecutionItem) res
        assertEquals('xyz/abc', item.getJobIdentifier())
        assertNotNull(item.args)
        assertEquals([], item.args as List)
        JobRefCommand jrc = (JobRefCommand) res
        jrc.project=='refProject'

        then:
        // above asserts have validation
        1 == 1
    }

    void testItemForWFCmdItem_jobref_nullproject() {
        when:
        def testService = service
        JobExec ce = new JobExec(jobName: 'abc', jobGroup: 'xyz', jobProject: null)
        def res = testService.itemForWFCmdItem(ce)
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertFalse(res instanceof ScriptURLCommandExecutionItem)
        assertTrue(res instanceof JobExecutionItem)
        JobExecutionItem item = (JobExecutionItem) res
        assertEquals('xyz/abc', item.getJobIdentifier())
        assertNotNull(item.args)
        assertEquals([], item.args as List)
        JobRefCommand jrc = (JobRefCommand) res
        jrc.project==null

        then:
        // above asserts have validation
        1 == 1
    }

    void testItemForWFCmdItem_jobref_setproject() {
        when:
        def testService = service
        JobExec ce = new JobExec(jobName: 'abc', jobGroup: 'xyz', jobProject: null)
        def res = testService.itemForWFCmdItem(ce, null, 'jobProject')
        assertNotNull(res)
        assertTrue(res instanceof StepExecutionItem)
        assertFalse(res instanceof ScriptURLCommandExecutionItem)
        assertTrue(res instanceof JobExecutionItem)
        JobExecutionItem item = (JobExecutionItem) res
        assertEquals('xyz/abc', item.getJobIdentifier())
        assertNotNull(item.args)
        assertEquals([], item.args as List)
        JobRefCommand jrc = (JobRefCommand) res
        jrc.project=='jobProject'

        then:
        // above asserts have validation
        1 == 1
    }

    void testItemForWFCmdItem_jobref_unmodified_original() {
        when:
        def testService = service
        JobExec ce = new JobExec(jobName: 'abc', jobGroup: 'xyz', jobProject: null)
        def res = testService.itemForWFCmdItem(ce, null, 'jobProject')

        then:
        assertNotNull(res)
        assertNull(ce.jobProject)
    }

    void testcreateExecutionItemForWorkflow() {
        when:
        def testService = service
        def project = 'test'
        def eh1= new JobExec([jobName: 'refhandler', jobGroup: 'grp', project: null,
                              argString: 'blah err4', keepgoingOnSuccess: false])
        Workflow w = new Workflow(
            keepgoing: true,
            commands: [
                new JobExec([jobName: 'refjob', jobGroup: 'grp', jobProject: project,
                             keepgoingOnSuccess: false ,errorHandler: eh1])
                ]
            )
        w.save()
        WorkflowExecutionItem res = testService.createExecutionItemForWorkflow(w, project)
        assertNotNull(res)
        assertNotNull(res.workflow)
        def item = res.workflow.commands[0]
        println(item)
        assertNotNull(item.failureHandler)
        println(item.failureHandler)

        then:
        assertNotNull(item.failureHandler.project)
        assertEquals(project,item.failureHandler.project)

    }
}


class MockForThreadOutputStream extends ThreadBoundOutputStream{

    /**
     * Create a ThreadBoundOutputStream with a particular OutputStream as the default write destination.
     *
     * @param stream default sink
     */
    MockForThreadOutputStream(OutputStream stream) {
        super(stream)
    }

    @Override
    OutputStream removeThreadStream() {
        return null
    }

    @Override
    void close() throws IOException {

    }
}
