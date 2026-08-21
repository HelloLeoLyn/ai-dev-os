package com.aidevos.orchestrator.planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SELF-HOSTING-GATE-BLOCKER-01-FIX：
 * targeted test 提取必须 fail-closed——只有明确测试类才生成 testClass。
 */
class NaturalGoalClassifierTest {

	/** 1. 显式测试文件 → 提取正确测试类（保持既有行为） */
	@Test
	void explicitTestFileProducesTarget() {
		assertEquals("V1FinalGateSmokeTest",
			NaturalGoalClassifier.extractTestTarget(
				"新增 V1FinalGateSmokeTest.java 冒烟测试文件并运行对应测试").orElseThrow());
		assertEquals("FooServiceTest",
			NaturalGoalClassifier.extractTestTarget("运行 FooServiceTest").orElseThrow());
		assertEquals("PricingIT",
			NaturalGoalClassifier.extractTestTarget("执行 PricingIT.java").orElseThrow());
	}

	/** 2. 普通 coding goal → 无 testClass（不出现 WRIT/API 等伪目标） */
	@Test
	void ordinaryReadOnlyApiGoalProducesNoTestTarget() {
		String goal = "在 services/orchestrator 中增加一个只读 API：\n\nGET /api/system/version\n\n"
			+ "返回：\n{\n  \"name\": \"AI Dev OS\",\n  \"version\": \"v1\"\n}\n\n"
			+ "要求：\n- 不修改现有核心调度逻辑\n- 增加最小测试\n- 使用 READ_WRITE\n"
			+ "- FAST validation\n- 不自动 merge";
		assertTrue(NaturalGoalClassifier.extractTestTarget(goal).isEmpty(),
			"普通 coding goal 绝不产出伪 testClass");
		// 防回归：WRITE/EXIT/COMMIT 等普通英文词不得因 IT 后缀被误提取
		assertTrue(NaturalGoalClassifier.extractTestTarget("请 READ_WRITE 模式下修改").isEmpty());
		assertTrue(NaturalGoalClassifier.extractTestTarget("完成后退出 EXIT 状态").isEmpty());
		assertTrue(NaturalGoalClassifier.extractTestTarget("请提交 COMMIT 修改").isEmpty());
		assertTrue(NaturalGoalClassifier.extractTestTarget("增加一个 API 接口").isEmpty());
		assertTrue(NaturalGoalClassifier.extractTestTarget("在 services/orchestrator 中").isEmpty());
	}
}
