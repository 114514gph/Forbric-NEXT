package net.neoforbric.boot;

import java.util.ArrayList;
import java.util.List;

import net.neoforbric.api.Ecosystem;
import net.neoforbric.api.LoadContext;
import net.neoforbric.api.LoadPhase;
import net.neoforbric.api.ModCandidate;
import net.neoforbric.api.ModLoader;

/**
 * 手写的 {@link ModLoader} 假对象（不引 Mockito）。
 *
 * <p>职责：记录自己被调用的顺序与参数，可选地在 runPhase 时抛异常，
 * 用来验证 boot orchestrator 的编排契约。
 */
class FakeModLoader implements ModLoader {

	private final Ecosystem ecosystem;
	private final List<ModCandidate> discovered;

	/** discover() 被调用的次数。 */
	int discoverCalls = 0;
	/** resolve() 收到的 candidates（应等于本 loader 自己 discover 出来的那份）。 */
	List<ModCandidate> resolvedCandidates;
	/** runPhase 实际被推进过的阶段（按顺序记录）。 */
	final List<LoadPhase> phasesRun = new ArrayList<>();
	/** 若为 true，runPhase 每次都抛 RuntimeException。 */
	boolean throwOnRunPhase = false;

	FakeModLoader(Ecosystem ecosystem, List<ModCandidate> discovered) {
		this.ecosystem = ecosystem;
		this.discovered = discovered;
	}

	@Override
	public Ecosystem ecosystem() {
		return ecosystem;
	}

	@Override
	public List<ModCandidate> discover(LoadContext context) {
		discoverCalls++;
		return discovered;
	}

	@Override
	public void resolve(List<ModCandidate> candidates, LoadContext context) {
		this.resolvedCandidates = candidates;
	}

	@Override
	public void runPhase(LoadPhase phase) {
		phasesRun.add(phase);
		if (throwOnRunPhase) {
			throw new RuntimeException(ecosystem + " 在 " + phase + " 炸了");
		}
	}
}
