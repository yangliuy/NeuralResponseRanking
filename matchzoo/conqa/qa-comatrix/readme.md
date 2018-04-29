### Code for computing the QA term PPMI co-occurrence matrix used in DMN-KD model
Author: [Chen Qu (UMass)](http://chenqu.me/)


The four folders are input and output folders for local_matrix.py

Sample inputs and outputs for local_matrix.py:

inputs:
ids: instance ids
qa_pairs_10: Q&A pairs for instances, format: {'instance_id': {'question': Example question..., 'answer': Example answer}}
terms: contexts terms and response terms for each instance, format: instance_id \t contexts terms \t response terms

outputs:
inter_unique: outputs in json, format: 


```
{
‘Instance_id’:{
			'rcv_dict': {key: ‘rIndex_cIndex’, value: m[rIndex][cIndex]}
			‘r_vocab': dict of vocabs for rows， [term2index]
			'c_vocab': dict of vocabs for cols,     [term2index]
			}
}
```
