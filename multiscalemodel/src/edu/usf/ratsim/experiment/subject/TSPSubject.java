package edu.usf.ratsim.experiment.subject;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.vecmath.Point3f;

import edu.usf.experiment.robot.LocalizableRobot;
import edu.usf.experiment.robot.Robot;
import edu.usf.experiment.subject.Subject;
import edu.usf.experiment.subject.affordance.Affordance;
import edu.usf.experiment.subject.affordance.EatAffordance;
import edu.usf.experiment.subject.affordance.ForwardAffordance;
import edu.usf.experiment.subject.affordance.TurnAffordance;
import edu.usf.experiment.utils.ElementWrapper;
import edu.usf.ratsim.nsl.modules.cell.PlaceCell;


public class TSPSubject extends Subject {

	private float step;
	private float leftAngle;
	private float rightAngle;
	
	private TSPModel model;

	public TSPSubject(String name, String group,
			ElementWrapper params, Robot robot) {
		super(name, group, params, robot);
		
		step = params.getChildFloat("step");
		leftAngle = params.getChildFloat("leftAngle");
		rightAngle = params.getChildFloat("rightAngle");
		
		if (!(robot instanceof LocalizableRobot))
			throw new RuntimeException("TSPSubject "
					+ "needs a Localizable Robot");
		LocalizableRobot lRobot = (LocalizableRobot) robot;

		model = new TSPModel(params, this, lRobot);
	}

	@Override
	public void stepCycle() {
		setHasEaten(false);
		clearTriedToEAt();
		
		model.simRun();
	}
	
	@Override
	public List<Affordance> getPossibleAffordances() {
		List<Affordance> res = new LinkedList<Affordance>();
		
		res.add(getLeftAffordance());
		res.add(getForwardAffordance());
		res.add(getRightAffordance());
		res.add(new EatAffordance());
		
		return res;
	}

	@Override
	public float getMinAngle() {
		return leftAngle;
	}

	@Override
	public void newEpisode() {
		model.newEpisode();
	}

	@Override
	public void newTrial() {
		model.newTrial();
	}

	@Override
	public Affordance getHypotheticAction(Point3f pos, float theta,
			int intention) {
		return null;
	}

	@Override
	public void deactivateHPCLayersRadial(LinkedList<Integer> indexList, float constant) {
		throw new NotImplementedException();
	}

	@Override
	public void setExplorationVal(float val) {
		throw new NotImplementedException();
	}

	@Override
	public float getStepLenght() {
		return step;
	}

	@Override
	public Map<Float,Float> getValue(Point3f point, int intention, float angleInterval, float distToWall) {
		throw new NotImplementedException();
	}

	public List<PlaceCell> getPlaceCells() {
		return model.getPlaceCells();
	}

	@Override
	public void deactivateHPCLayersProportion(LinkedList<Integer> indexList,
			float proportion) {
		throw new NotImplementedException();
	}

	@Override
	public void remapLayers(LinkedList<Integer> indexList) {
		throw new NotImplementedException();
	}

	public Map<Integer, Float> getPCActivity() {
		return model.getCellActivation();
	}

	@Override
	public float getValueEntropy() {
		throw new NotImplementedException();
	}

	@Override
	public void reactivateHPCLayers(LinkedList<Integer> indexList) {
		throw new NotImplementedException();
	}
	
	
	@Override
	public Affordance getForwardAffordance() {
		return new ForwardAffordance(step);
	}

	@Override
	public Affordance getLeftAffordance() {
		return new TurnAffordance(leftAngle, step);
	}

	@Override
	public Affordance getRightAffordance() {
		return new TurnAffordance(rightAngle, step);
	}


}
