package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.aidevos.orchestrator.approval.*;
import com.aidevos.orchestrator.plan.approval.*;
import com.aidevos.orchestrator.plan.run.*;
import com.aidevos.orchestrator.planner.replan.*;
import com.aidevos.orchestrator.tool.approval.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresCodingApprovalRepository implements CodingApprovalRepository {
	private static final String TYPE="coding-approval"; private final PostgresDocumentStore store;
	PostgresCodingApprovalRepository(PostgresDocumentStore store){this.store=store;}
	public void save(CodingApprovalRequest v){store.put(TYPE,v.getId(),PersistenceSnapshots.CodingApproval.of(v),v.getJobId()==null?"task:"+v.getTaskId():"job:"+v.getJobId());}
	public CodingApprovalRequest get(String id){var v=store.get(TYPE,id,PersistenceSnapshots.CodingApproval.class);return v==null?null:v.value();}
	public List<CodingApprovalRequest> getAll(){return store.all(TYPE,PersistenceSnapshots.CodingApproval.class).stream().map(PersistenceSnapshots.CodingApproval::value).toList();}
	public CodingApprovalRequest findReusable(String taskId,String jobId){String key=jobId==null?"task:"+taskId:"job:"+jobId;return store.allBySecondary(TYPE,key,PersistenceSnapshots.CodingApproval.class).stream().map(PersistenceSnapshots.CodingApproval::value).filter(v->v.getStatus()==ApprovalStatus.PENDING||v.getStatus()==ApprovalStatus.APPROVED).reduce((a,b)->b).orElse(null);}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresToolApprovalRepository implements ToolApprovalRepository {
	private static final String TYPE="tool-approval"; private final PostgresDocumentStore store;
	PostgresToolApprovalRepository(PostgresDocumentStore store){this.store=store;}
	public void save(ToolApprovalRequest v){store.put(TYPE,v.getId(),PersistenceSnapshots.ToolApproval.of(v),v.getJobId()==null?"invocation:"+v.getInvocationId():"job:"+v.getJobId());}
	public ToolApprovalRequest get(String id){var v=store.get(TYPE,id,PersistenceSnapshots.ToolApproval.class);return v==null?null:v.value();}
	public List<ToolApprovalRequest> getAll(){return store.all(TYPE,PersistenceSnapshots.ToolApproval.class).stream().map(PersistenceSnapshots.ToolApproval::value).toList();}
	public ToolApprovalRequest findReusable(String jobId,String invocationId,String providerId,String toolName,String argumentsHash,String workspace,String permissionLevel){String key=jobId==null?"invocation:"+invocationId:"job:"+jobId;return store.allBySecondary(TYPE,key,PersistenceSnapshots.ToolApproval.class).stream().map(PersistenceSnapshots.ToolApproval::value).filter(v->same(v.getJobId(),jobId)&&v.getInvocationId().equals(invocationId)&&v.getProviderId().equals(providerId)&&v.getToolName().equals(toolName)&&v.getArgumentsHash().equals(argumentsHash)&&same(v.getWorkspace(),workspace)&&v.getPermissionLevel().equals(permissionLevel)).filter(v->v.getStatus()==ApprovalStatus.PENDING||v.getStatus()==ApprovalStatus.APPROVED).reduce((a,b)->b).orElse(null);}
	private boolean same(String a,String b){return a==null?b==null:a.equals(b);}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresPlanApprovalRepository implements PlanApprovalRepository {
	private static final String TYPE="plan-approval"; private final PostgresDocumentStore store;
	private final DataSource dataSource;
	PostgresPlanApprovalRepository(PostgresDocumentStore store,DataSource dataSource){this.store=store;this.dataSource=dataSource;}
	public synchronized PlanApprovalRequest save(PlanApprovalRequest v){String key=v.getPlanId()+":"+v.getPlanVersion();store.freezePlanVersion(key,v.getPlanSnapshotHash());PlanApprovalRequest existing=find(v.getPlanId(),v.getPlanVersion(),v.getRequestId(),v.getPlanSnapshotHash());if(existing!=null&&!existing.getId().equals(v.getId()))return existing;store.put(TYPE,v.getId(),PersistenceSnapshots.PlanApproval.of(v),key);return v;}
	public PlanApprovalRequest get(String id){var v=store.get(TYPE,id,PersistenceSnapshots.PlanApproval.class);return v==null?null:v.value();}
	public List<PlanApprovalRequest> getAll(){return store.all(TYPE,PersistenceSnapshots.PlanApproval.class).stream().map(PersistenceSnapshots.PlanApproval::value).toList();}
	public PlanApprovalRequest find(String planId,int version,String requestId,String hash){return store.allBySecondary(TYPE,planId+":"+version,PersistenceSnapshots.PlanApproval.class).stream().map(PersistenceSnapshots.PlanApproval::value).filter(v->v.getRequestId().equals(requestId)&&v.getPlanSnapshotHash().equals(hash)).findFirst().orElse(null);}
	@Override
	public boolean consumeIfApproved(String id){
		String sql="UPDATE repository_documents SET payload=jsonb_set(payload,'{status}','\"CONSUMED\"'::jsonb,true),updated_at=CURRENT_TIMESTAMP "
			+ "WHERE repository_type='plan-approval' AND entity_id=? AND payload->>'status'='APPROVED'";
		try(Connection connection=dataSource.getConnection();PreparedStatement statement=connection.prepareStatement(sql)){
			statement.setString(1,id);
			return statement.executeUpdate()==1;
		}catch(SQLException exception){throw new IllegalStateException("PostgreSQL plan approval failed to consume "+id,exception);}
	}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresReplanRequestRepository implements ReplanRequestRepository {
	private static final String TYPE="replan-request"; private final PostgresDocumentStore store;
	PostgresReplanRequestRepository(PostgresDocumentStore store){this.store=store;}
	public void save(ReplanRequest v){store.put(TYPE,v.id(),v,v.failedPlanRunId());} public ReplanRequest get(String id){return store.get(TYPE,id,ReplanRequest.class);}
	public List<ReplanRequest> getAll(){return store.all(TYPE,ReplanRequest.class);} public ReplanRequest findByPlanRun(String id){return store.allBySecondary(TYPE,id,ReplanRequest.class).stream().findFirst().orElse(null);}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresPlanRunRepository implements PlanRunRepository {
	private static final String TYPE="plan-run"; private final PostgresDocumentStore store;
	private final DataSource dataSource; private final ObjectMapper mapper;
	PostgresPlanRunRepository(PostgresDocumentStore store,DataSource dataSource,ObjectMapper mapper){this.store=store;this.dataSource=dataSource;this.mapper=mapper;}
	public synchronized void create(String approvalId,PlanRun run){if(findRunIdByApproval(approvalId)!=null)throw new IllegalStateException("Plan approval has already started a run");createIfAbsent(approvalId,run);}
	public PlanRun createIfAbsent(String approvalId,PlanRun run){
		String sql="INSERT INTO repository_documents(repository_type,entity_id,payload,secondary_key,version) "
			+ "VALUES ('plan-run',?,?::jsonb,?,0) ON CONFLICT (secondary_key) WHERE repository_type='plan-run' DO NOTHING";
		try(Connection connection=dataSource.getConnection();PreparedStatement statement=connection.prepareStatement(sql)){
			statement.setString(1,run.getId());statement.setString(2,payload(run));statement.setString(3,approvalId);
			if(statement.executeUpdate()==1)return run;
			PlanRun existing=getByApproval(approvalId);
			return existing==null?run:existing;
		}catch(Exception exception){throw failure("create run",exception);}
	}
	public void save(PlanRun v){store.put(TYPE,v.getId(),PersistenceSnapshots.Run.of(v),v.getApprovalId());}
	public boolean saveIfUnchanged(PlanRun run,int expectedVersion){
		String sql="UPDATE repository_documents SET payload=?::jsonb,secondary_key=?,version=version+1,updated_at=CURRENT_TIMESTAMP "
			+ "WHERE repository_type='plan-run' AND entity_id=? AND version=?";
		try(Connection connection=dataSource.getConnection();PreparedStatement statement=connection.prepareStatement(sql)){
			statement.setString(1,payload(run));statement.setString(2,run.getApprovalId());statement.setString(3,run.getId());statement.setInt(4,expectedVersion);
			if(statement.executeUpdate()==1){run.bumpVersion();return true;}
			return false;
		}catch(Exception exception){throw failure("CAS save run",exception);}
	}
	public PlanRun get(String id){return selectOne("SELECT payload,version FROM repository_documents WHERE repository_type='plan-run' AND entity_id=?",id);}
	public List<PlanRun> getAll(){return select("SELECT payload,version FROM repository_documents WHERE repository_type='plan-run' ORDER BY created_at,entity_id");}
	public String findRunIdByApproval(String id){PlanRun run=getByApproval(id);return run==null?null:run.getId();}
	public Optional<PlanRun> claimCoordinator(String runId,String owner,Instant now,Duration leaseDuration){
		PlanRun current=get(runId);
		if(current==null)return Optional.empty();
		if(current.getCoordinatorOwner()!=null&&current.getCoordinatorExpiresAt()!=null
				&&current.getCoordinatorExpiresAt().isAfter(now)&&!current.getCoordinatorOwner().equals(owner)){
			return Optional.empty();
		}
		current.applyCoordinatorLease(owner,current.getCoordinatorToken()+1,now.plus(leaseDuration));
		return saveIfUnchanged(current,current.getVersion())?Optional.of(current):Optional.empty();
	}
	public boolean releaseCoordinator(String runId,String owner,long token){
		PlanRun current=get(runId);
		if(current==null||!owner.equals(current.getCoordinatorOwner())||current.getCoordinatorToken()!=token)return false;
		current.clearCoordinatorLease();
		return saveIfUnchanged(current,current.getVersion());
	}
	public void remove(String approvalId,String runId){store.delete(TYPE,runId);}
	private PlanRun getByApproval(String approvalId){return selectOne("SELECT payload,version FROM repository_documents WHERE repository_type='plan-run' AND secondary_key=?",approvalId);}
	private PlanRun selectOne(String sql,Object... parameters){List<PlanRun> values=select(sql,parameters);return values.isEmpty()?null:values.getFirst();}
	private List<PlanRun> select(String sql,Object... parameters){
		try(Connection connection=dataSource.getConnection();PreparedStatement statement=connection.prepareStatement(sql)){
			bind(statement,parameters);
			try(ResultSet result=statement.executeQuery()){
				List<PlanRun> values=new java.util.ArrayList<>();
				while(result.next())values.add(read(result));
				return values;
			}
		}catch(Exception exception){throw failure("read runs",exception);}
	}
	private PlanRun read(ResultSet result)throws Exception{
		PlanRun run=mapper.readValue(result.getString(1),PersistenceSnapshots.Run.class).value();
		run.setVersion(result.getInt(2));
		return run;
	}
	private void bind(PreparedStatement statement,Object... parameters)throws SQLException{
		for(int index=0;index<parameters.length;index++)statement.setObject(index+1,parameters[index]);
	}
	private String payload(PlanRun run)throws Exception{return mapper.writeValueAsString(PersistenceSnapshots.Run.of(run));}
	private IllegalStateException failure(String operation,Exception cause){return new IllegalStateException("PostgreSQL plan run failed to "+operation,cause);}
}
