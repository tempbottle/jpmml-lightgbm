/*
 * Copyright (c) 2017 Villu Ruusmann
 *
 * This file is part of JPMML-LightGBM
 *
 * JPMML-LightGBM is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-LightGBM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-LightGBM.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.lightgbm;

import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class Tree {

	private int num_leaves_;

	private int[] left_child_;

	private int[] right_child_;

	private int[] split_feature_real_;

	private double[] threshold_;

	private double[] leaf_value_;

	private int[] leaf_count_;

	private double[] internal_value_;

	private int[] internal_count_;


	public void load(Section section){
		this.num_leaves_ = section.getInt("num_leaves");

		this.left_child_ = section.getIntArray("left_child", this.num_leaves_ - 1);
		this.right_child_ = section.getIntArray("right_child", this.num_leaves_ - 1);
		this.split_feature_real_ = section.getIntArray("split_feature", this.num_leaves_ - 1);
		this.threshold_ = section.getDoubleArray("threshold", this.num_leaves_ - 1);
		this.leaf_value_ = section.getDoubleArray("leaf_value", this.num_leaves_);
		this.leaf_count_ = section.getIntArray("leaf_count", this.num_leaves_);
		this.internal_value_ = section.getDoubleArray("internal_value", this.num_leaves_ - 1);
		this.internal_count_ = section.getIntArray("internal_count", this.num_leaves_ - 1);
	}

	public TreeModel encodeTreeModel(Schema schema){
		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, 0, schema);

		TreeModel treeModel = new TreeModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	public void encodeNode(Node parent, int index, Schema schema){
		parent.setId(String.valueOf(index));

		// Non-leaf (aka internal) node
		if(index >= 0){
			parent.setScore(null); // XXX
			parent.setRecordCount((double)this.internal_count_[index]);

			Feature feature = schema.getFeature(this.split_feature_real_[index]);

			Predicate leftPredicate;
			Predicate rightPredicate;

			if(feature instanceof BinaryFeature){
				BinaryFeature binaryFeature = (BinaryFeature)feature;

				if(this.threshold_[index] != 0.5d){
					throw new IllegalArgumentException();
				}

				leftPredicate = new SimplePredicate(binaryFeature.getName(), SimplePredicate.Operator.NOT_EQUAL)
					.setValue(binaryFeature.getValue());

				rightPredicate = new SimplePredicate(binaryFeature.getName(), SimplePredicate.Operator.EQUAL)
					.setValue(binaryFeature.getValue());
			} else

			{
				ContinuousFeature continuousFeature = feature.toContinuousFeature();

				String value = ValueUtil.formatValue(this.threshold_[index]);

				leftPredicate = new SimplePredicate(continuousFeature.getName(), SimplePredicate.Operator.LESS_OR_EQUAL)
					.setValue(value);

				rightPredicate = new SimplePredicate(continuousFeature.getName(), SimplePredicate.Operator.GREATER_THAN)
					.setValue(value);
			}

			Node leftChild = new Node()
				.setPredicate(leftPredicate);

			encodeNode(leftChild, this.left_child_[index], schema);

			Node rightChild = new Node()
				.setPredicate(rightPredicate);

			encodeNode(rightChild, this.right_child_[index], schema);

			parent.addNodes(leftChild, rightChild);
		} else

		// Leaf node
		{
			index = ~index;

			parent.setScore(ValueUtil.formatValue(this.leaf_value_[index]));
			parent.setRecordCount((double)this.leaf_count_[index]);
		}
	}

	Boolean isBinary(int feature){
		Boolean result = null;

		for(int i = 0; i < this.split_feature_real_.length; i++){

			if(this.split_feature_real_[i] == feature){

				if(this.threshold_[i] != 0.5d){
					return Boolean.FALSE;
				}

				result = Boolean.TRUE;
			}
		}

		return result;
	}
}