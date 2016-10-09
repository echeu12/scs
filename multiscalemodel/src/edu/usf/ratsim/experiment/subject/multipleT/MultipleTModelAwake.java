package edu.usf.ratsim.experiment.subject.multipleT;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.media.j3d.VirtualUniverse;

import edu.usf.experiment.robot.LocalizableRobot;
import edu.usf.experiment.subject.Subject;
import edu.usf.experiment.universe.Universe;
import edu.usf.experiment.utils.ElementWrapper;
import edu.usf.micronsl.module.Module;
import edu.usf.micronsl.module.copy.Float1dCopyModule;
import edu.usf.micronsl.module.copy.Float1dSparseCopyModule;
import edu.usf.micronsl.module.copy.Int0dCopyModule;
import edu.usf.micronsl.port.onedimensional.sparse.Float1dSparsePortMap;
import edu.usf.micronsl.port.twodimensional.FloatMatrixPort;
import edu.usf.ratsim.experiment.universe.virtual.VirtUniverse;
import edu.usf.ratsim.experiment.universe.virtual.drawingUtilities.DrawPolarGraph;
import edu.usf.ratsim.nsl.modules.actionselection.ActionFromProbabilities;
import edu.usf.ratsim.nsl.modules.actionselection.ProportionalVotes;
import edu.usf.ratsim.nsl.modules.actionselection.Softmax;
import edu.usf.ratsim.nsl.modules.cell.PlaceCell;
import edu.usf.ratsim.nsl.modules.celllayer.TmazeRandomPlaceCellLayer;
import edu.usf.ratsim.nsl.modules.input.Position;
import edu.usf.ratsim.nsl.modules.input.SubjectAte;
import edu.usf.ratsim.nsl.modules.multipleT.ActionGatingModule;
import edu.usf.ratsim.nsl.modules.multipleT.DontGoBackBiasModule;
import edu.usf.ratsim.nsl.modules.multipleT.MultipleTActionPerformer;
import edu.usf.ratsim.nsl.modules.multipleT.PlaceCellTransitionMatrixUpdater;
import edu.usf.ratsim.nsl.modules.multipleT.UpdateQModule;
import edu.usf.ratsim.nsl.modules.rl.Reward;
import edu.usf.ratsim.nsl.modules.rl.SarsaQDeltaError;

public class MultipleTModelAwake extends MultipleTModel {

	public TmazeRandomPlaceCellLayer placeCells;
	
	public ProportionalVotes currentStateQ;
	
	private float[][] QTable;
	private float[][] WTable;

	public MultipleTModelAwake() {
	}

	public MultipleTModelAwake(ElementWrapper params, Subject subject,
			LocalizableRobot lRobot, int numActions,int numPC) {
		
		//Get parameters frorm xml file
		float PCRadius 			= params.getChildFloat("PCRadius");
		String placeCellType 	= params.getChildText("placeCells");
		
		float discountFactor	= params.getChildFloat("discountFactor");
		float learningRate		= params.getChildFloat("learningRate");
		float wTransitionLR		= params.getChildFloat("wTransitionLR");
		float foodReward		= params.getChildFloat("foodReward");
		
		float sameActionBias 	= params.getChildFloat("sameActionBias");
		
		
		
		//Model overview:
		
		/**
		 * Replay Model Johnson Redish 2005:
		 * 
		 * Variables:
		 * 		Q 			Table
		 * 		W 			Transition Table
		 *      sleepState 	State: AWAKE/SLEEPING
		 * MODEL:
		 * Choose random cell
		 * 
		 * 
		 * activate cell
		 * propagate activity
		 * 
		 * 																									              Copy of Action and placeCells
		 * 																											                \/
		 * Reward-------------------------------->*---->*------------------------------------------->*--------->*->deltaSignal----->UpdateQ
		 * 			                             /\    /\                              	  			/\         /\
		 *                                        |     |                                			 |          |
		 * 	          PCCopy--*-> UpdateW		Qcopy 	|							  			  ActionCopy    |
		 *				 |	 /     				  |		|					  			 			 |			|	 
		 * 				\/	/	   				 \/		|					  		 				\/          |
		 * Pos---->	PlaceCells---------------->currentStateQ-->SoftMax-->ActionGating-->bias------>ActionSelection----->ActionPerformer--->subAte
		 * 
		 * 
		 * NOTES:
		 * 		-The model is only a reference to understand the flow, modules do not correspond 1 to 1 with the model components
		 * 		-Sleep Action should only get calculated when SLEEPING
		 * 		-calcCombinedQ, SoftMax and updataW should only be performed while awake
		 * 		-subAte = subjectAte (already existing module)
		 * 		-backDep = backward dependency
		 * 		-actionGating checks weather an action can be performed or not before action selection
		 * 		-UpdateQ requires Qcopy and actionCopy or currentStateQ and 
		 * 		-Reward receives input from subAte but executes before
		 */
		
		
		//Create Variables Q,W, note sleepState has already been initialized.

		QTable = ((MultipleTSubject)subject).QTable;
		FloatMatrixPort QPort = new FloatMatrixPort((Module) null, QTable);
		
		WTable = ((MultipleTSubject)subject).WTable;
		FloatMatrixPort WPort = new FloatMatrixPort((Module)null, WTable);
		
		
		//create subAte module
		SubjectAte subAte = new SubjectAte("Subject Ate",subject);
		addModule(subAte);
		
		//Create reward module
		float nonFoodReward = 0;
		Reward r = new Reward("foodReward", foodReward, nonFoodReward);
		r.addInPort("subAte", subAte.getOutPort("subAte"),true); // reward must execute before subAte (the reward obtained depends if we ate in the previous action)
		addModule(r);
		
		
		//Create pos module 
		Position pos = new Position("position", lRobot);
		addModule(pos);
		
		//Create Place Cells module
		placeCells = new TmazeRandomPlaceCellLayer("PCLayer", PCRadius, numPC, placeCellType);
		placeCells.addInPort("position", pos.getOutPort("position"));
		addModule(placeCells);
		
		//System.out.println("rad " + placeCells.getCells().get(0).getPlaceRadius());
		
		//Create PC copy module
		Float1dSparseCopyModule pcCopy = new Float1dSparseCopyModule("PCCopy");
		pcCopy.addInPort("toCopy",placeCells.getOutPort("activation"), true);
		addModule(pcCopy);
		
		
		//Create currentStateQ Q module
		currentStateQ = new ProportionalVotes("currentStateQ",numActions,true);
		currentStateQ.addInPort("states", placeCells.getOutPort("activation"));
		currentStateQ.addInPort("value", QPort);
		addModule(currentStateQ);
		
		//Create copy Q module
		Module copyQ = new Float1dCopyModule("copyQ");
		copyQ.addInPort("toCopy", currentStateQ.getOutPort("votes"),true);
		addModule(copyQ);
		
		
		//Create SoftMax module
		Softmax softmax = new Softmax("softmax", numActions);
		softmax.addInPort("input", currentStateQ.getOutPort("votes"));
		addModule(softmax);
		
		
		//create sameActionBias module:
		//Assigns bias chance of choosing previous action and (1-bias) of using current probabilities
		//must be done before deleting impossible actions otherwise if the old action is 
		//now impossible it will have 50% chance to be taken again
		//CurrentActionBiasModule biasModule = new CurrentActionBiasModule("bias",numActions,sameActionBias);
		//biasModule.addInPort("input", softmax.getOutPort("probabilities")); 
		//addModule(biasModule);
		//need to add old action input created later
		
		
		//Create ActionGatingModule -- sets the probabilities of impossible actions to 0 and then normalizes them
		ActionGatingModule actionGating = new ActionGatingModule("actionGating", numActions, ((MultipleTSubject)subject).step);
		actionGating.addInPort("input", softmax.getOutPort("probabilities"));
		addModule(actionGating);
		
		
		//Add bias module to probabilities
		DontGoBackBiasModule biasModule = new DontGoBackBiasModule("bias", numActions, 7, 0.01f);
		biasModule.addInPort("input", actionGating.getOutPort("probabilities"));
		addModule(biasModule);
		
		
		//Create action selection module -- choose action according to probability distribution
		Module actionSelection = new ActionFromProbabilities("actionFromProbabilities");
		actionSelection.addInPort("probabilities", biasModule.getOutPort("probabilities"));
		addModule(actionSelection);
		
		
		//Create actionCopyModule
		Int0dCopyModule actionCopy = new Int0dCopyModule("actionCopy");
		actionCopy.addInPort("toCopy", actionSelection.getOutPort("action"),true);
		addModule(actionCopy);
		
		//Add extra input to bias Module
		biasModule.addInPort("action", actionCopy.getOutPort("copy"));
		
		
		
		//Create deltaSignal module
		Module deltaError = new SarsaQDeltaError("error", discountFactor);
		deltaError.addInPort("reward", r.getOutPort("reward"));
		deltaError.addInPort("copyQ", copyQ.getOutPort("copy"));
		deltaError.addInPort("Q",currentStateQ.getOutPort("votes"));
		deltaError.addInPort("copyAction", actionCopy.getOutPort("copy"));
		deltaError.addInPort("action",actionSelection.getOutPort("action"));
		addModule(deltaError);
		
		
		//Create update Q module
		Module updateQ = new UpdateQModule("updateQ", numActions, learningRate);
		updateQ.addInPort("delta", deltaError.getOutPort("delta"));
		updateQ.addInPort("action", actionCopy.getOutPort("copy"));
		updateQ.addInPort("Q", QPort);
		updateQ.addInPort("placeCells", pcCopy.getOutPort("copy"));
		addModule(updateQ);
		
		
		
		//Create Action Performer module
		MultipleTActionPerformer actionPerformer = new MultipleTActionPerformer("actionPerformer", numActions, ((MultipleTSubject)subject).step, subject);
		actionPerformer.addInPort("action", actionSelection.getOutPort("action"));
		addModule(actionPerformer);
		
		subAte.addPreReq(actionPerformer);
		

		//Create UpdateW module
		PlaceCellTransitionMatrixUpdater wUpdater = new PlaceCellTransitionMatrixUpdater("wUpdater", numPC, wTransitionLR);
		wUpdater.addInPort("PC", placeCells.getOutPort("activation"));
		wUpdater.addInPort("PCcopy", pcCopy.getOutPort("copy"));
		wUpdater.addInPort("wPort", WPort);
		addModule(wUpdater);
		
		
		
		//Add drawing utilities:
		VirtUniverse universe = VirtUniverse.getInstance();
		universe.addDrawingFunction(new DrawPolarGraph("Q softmax",50, 50, 50, softmax.probabilities,true));
		
		universe.addDrawingFunction(new DrawPolarGraph("gated probs",50, 170, 50, actionGating.probabilities,true));
		universe.addDrawingFunction(new DrawPolarGraph("biased probs",50, 290, 50, biasModule.probabilities,true));
		
		
				
		

	}

	public void newTrial() {
		getModule("PCLayer").getOutPort("activation").clear();
		getModule("PCCopy").getOutPort("copy").clear();
		//by doing this deltaQ(s_i,a_i) = nu*delta*State(s_i)*<a_i,a> = 0
		
		((PlaceCellTransitionMatrixUpdater)getModule("wUpdater")).newTrial();
		
		
		//need to let the bias module know that a new episode started (do not bias on fisrt turn)
		((DontGoBackBiasModule)getModule("bias")).newTrial();
		
		
		
	}

	public List<PlaceCell> getPlaceCells() {
		return placeCells.getCells();
	}

	public void newEpisode() {
		// TODO Auto-generated method stub
		
		getModule("PCLayer").getOutPort("activation").clear();
		getModule("PCCopy").getOutPort("copy").clear();
		//by doing this deltaQ(s_i,a_i) = nu*delta*State(s_i)*<a_i,a> = 0
		
		((PlaceCellTransitionMatrixUpdater)getModule("wUpdater")).newEpisode();
		
		//need to let the bias module know that a new episode started (do not bias on fisrt turn)
		((DontGoBackBiasModule)getModule("bias")).newEpisode();
		
	}

	public Map<Integer, Float> getCellActivation() {
		Map<Integer, Float> activation = new LinkedHashMap<Integer, Float>();
		activation.putAll(((Float1dSparsePortMap) placeCells
					.getOutPort("activation")).getNonZero());
		return activation;
	}



}