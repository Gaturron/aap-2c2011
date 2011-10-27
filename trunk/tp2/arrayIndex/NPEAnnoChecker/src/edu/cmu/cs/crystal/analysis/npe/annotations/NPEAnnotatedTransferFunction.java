package edu.cmu.cs.crystal.analysis.npe.annotations;

import java.util.List;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import edu.cmu.cs.crystal.annotations.AnnotationDatabase;
import edu.cmu.cs.crystal.annotations.AnnotationSummary;
import edu.cmu.cs.crystal.cfg.eclipse.Label;
import edu.cmu.cs.crystal.flow.ILabel;
import edu.cmu.cs.crystal.flow.ILatticeOperations;
import edu.cmu.cs.crystal.flow.IResult;
import edu.cmu.cs.crystal.flow.LabeledResult;
import edu.cmu.cs.crystal.flow.LabeledSingleResult;
import edu.cmu.cs.crystal.simple.TupleLatticeElement;
import edu.cmu.cs.crystal.simple.TupleLatticeOperations;
import edu.cmu.cs.crystal.tac.AbstractTACBranchSensitiveTransferFunction;
import edu.cmu.cs.crystal.tac.model.ArrayInitInstruction;
import edu.cmu.cs.crystal.tac.model.BinaryOperation;
import edu.cmu.cs.crystal.tac.model.CopyInstruction;
import edu.cmu.cs.crystal.tac.model.LoadFieldInstruction;
import edu.cmu.cs.crystal.tac.model.LoadLiteralInstruction;
import edu.cmu.cs.crystal.tac.model.NewArrayInstruction;
import edu.cmu.cs.crystal.tac.model.Variable;

public class NPEAnnotatedTransferFunction extends AbstractTACBranchSensitiveTransferFunction<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> {
	/**
	 * The operations for this lattice. We want to have a tuple lattice from variables to null lattice elements, so we
	 * give it an instance of NullLatticeOperations. We also want the default value to be maybe null.
	 */
	TupleLatticeOperations<Variable, ArrayBoundsLatticeElement> ops =
		new TupleLatticeOperations<Variable, ArrayBoundsLatticeElement>(new ArrayBoundsLatticeOperations(), ArrayBoundsLatticeElement.bottom());
	private AnnotationDatabase annoDB;
	
	public NPEAnnotatedTransferFunction(AnnotationDatabase annoDB) {
		this.annoDB = annoDB;
	}	

	/**
	 * The operations will create a default lattice which will map all variables to maybe null (since that was our default).
	 * 
	 * Of course, "this" should never be null.
	 */
	public TupleLatticeElement<Variable, ArrayBoundsLatticeElement> createEntryValue(
			MethodDeclaration method) {
		TupleLatticeElement<Variable, ArrayBoundsLatticeElement> def = ops.getDefault();
		def.put(getAnalysisContext().getThisVariable(), ArrayBoundsLatticeElement.top());
		
		AnnotationSummary summary = annoDB.getSummaryForMethod(method.resolveBinding());
		
		for (int ndx = 0; ndx < method.parameters().size(); ndx++) {
			SingleVariableDeclaration decl = (SingleVariableDeclaration) method.parameters().get(ndx);
			Variable paramVar = getAnalysisContext().getSourceVariable(decl.resolveBinding());
			
			if (paramVar.resolveType().isArray()) {
				def.put(paramVar, ArrayBoundsLatticeElement.bottom());
			} else {
				def.put(paramVar, ArrayBoundsLatticeElement.top());
			}
		}
		
		return def;
	}

	/**
	 * Just return our lattice ops.
	 */
	public ILatticeOperations<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> getLatticeOperations() {
		return ops;
	}

	@Override
	public IResult<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> transfer(
			LoadFieldInstruction instr,
			List<ILabel> labels,
			TupleLatticeElement<Variable, ArrayBoundsLatticeElement> value) {
	
		if (instr.getFieldName().equals("length")) {
			System.out.println("llama a length "+instr.getAccessedObjectOperand());
		}
		value.put(instr.getTarget(), value.get(instr.getSourceObject()));
		return LabeledSingleResult.createResult(value,labels);
	}
	
	@Override
	public IResult<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> transfer(
			BinaryOperation instr,
			List<ILabel> labels,
			TupleLatticeElement<Variable, ArrayBoundsLatticeElement> value) {
		System.out.println("BinOp: "+instr.getOperator()+" "+instr.getOperand1()+" "+instr.getOperand2());
		ArrayBoundsLatticeElement res = ArrayBoundsLatticeElement.top();
		ArrayBoundsLatticeElement izq = value.get(instr.getOperand1());
		ArrayBoundsLatticeElement der = value.get(instr.getOperand2());
		switch(instr.getOperator())	
		{
		case ARIT_ADD:
			res = izq.add(der);
			break;
			
		case ARIT_SUBTRACT:
			res = izq.substract(der);
			break;
			
		case ARIT_MULTIPLY:
			res = izq.multiply(der);
			break;

		case REL_EQ:
		case REL_LT:
		case REL_GT:
		case REL_GEQ:
		case REL_LEQ:
			res = izq.getInterval(instr.getOperator(), der);
			break;
		}
		
		if (labels.size() > 1) {
			LabeledResult<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> ret = LabeledResult.createResult(labels,value);
			TupleLatticeElement<Variable, ArrayBoundsLatticeElement> valueTrue = ops.copy(value);
			valueTrue.put(instr.getOperand1(), res);
			ret.put(labels.get(0), valueTrue);
			return ret;				
		}
		else {
			value.put(instr.getTarget(), res);
			return LabeledSingleResult.createResult(value, labels);
		}
	}
		
	@Override
	public IResult<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> transfer(
			ArrayInitInstruction instr,
			List<ILabel> labels,
			TupleLatticeElement<Variable, ArrayBoundsLatticeElement> value) {
		
		if (instr.getInitOperands().size() > 0)
			value.put(instr.getTarget(), new ArrayBoundsLatticeElement(0, instr.getInitOperands().size()-1) );
		else
			value.put(instr.getTarget(), ArrayBoundsLatticeElement.bottom() );
		return LabeledSingleResult.createResult(value,labels);
	}

	@Override
	public IResult<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> transfer(
			CopyInstruction instr,
			List<ILabel> labels,
			TupleLatticeElement<Variable, ArrayBoundsLatticeElement> value) {
		value.put(instr.getTarget(), value.get(instr.getOperand()));
		return LabeledSingleResult.createResult(value,labels);
	}

	@Override
	public IResult<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> transfer(
			LoadLiteralInstruction instr,
			List<ILabel> labels,
			TupleLatticeElement<Variable, ArrayBoundsLatticeElement> value) {
		 
		if (instr.isNumber() && instr.getLiteral() instanceof java.lang.String) {
			int index = Integer.parseInt((String)instr.getLiteral());
			value.put(instr.getTarget(), new ArrayBoundsLatticeElement(index,index));
		}
		return LabeledSingleResult.createResult(value,labels);
	}

	@Override
	public IResult<TupleLatticeElement<Variable, ArrayBoundsLatticeElement>> transfer(
			NewArrayInstruction instr,
			List<ILabel> labels,
			TupleLatticeElement<Variable, ArrayBoundsLatticeElement> value) {
		
		Variable dim = instr.getDimensionOperands().get(0);
		ArrayBoundsLatticeElement adim = value.get(dim);
		value.put(instr.getTarget(), adim.merge(new ArrayBoundsLatticeElement(1, 1)).substract(new ArrayBoundsLatticeElement(1, 1)));
		
		return LabeledSingleResult.createResult(value,labels);
	}
	}