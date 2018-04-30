### Code for Computing the QA Term PPMI Co-occurrence Matrix Used in DMN-KD Model
Author: [Chen Qu (UMass)](https://chenqu.me/)

The ```local_matrix.py``` computes Positive Pointwise [Mutual Information](https://en.wikipedia.org/wiki/Mutual_information) (PPMI) for a response candidate and a dialog utterance, which is a part of the DMN-KD model. For an overview of DMN-KD, please refer to the repository readme.

##### Usage
```
$ python local_matrix run_id dataset unique qa_pair_num
parameters:
- run_id: partition id for input files (please refer to the Note section above)
- data: msdialog or udc
- unique: unique or nounique whether to use unique term co-occurrence count when computing PPMI. For example, given sentence A of "a a b c" and sentence B of "p p q m", the co-occurrence count for "a"  and "p" under option "unique"" is 1 while 4 under option "nounique".
- qa_pair_num: how many QA pairs to consider for each instance.
```

##### Data Preparation and Input
```local_matrix.py``` does not directly use the training/validation/test data of MSDialog and UDC. We preproess the data as follows:
1. Generate instance ID. A instance refer to a ```(label, context, candidate_reponse)``` triplet in the training/validation/test data. Instance ID is denoted as ```train/valid/test/ + line_number```. For example, the first instance (the first line) in training data is ```train_1```. An example for instance ID file is under ```ids/```. The second column of the id file is not used.
2. Retrieve QA pairs. Use the response candidate as the query to retrieve a set of relevant QA pairs from the external collection (e.g. [AskUbuntu](https://askubuntu.com/) dump for UDC). The question term and answer term co-occurrences can be modeled as PPMI. Please to refer to our paper for a formal explanation. An example for instance QA pairs file is under ```qa_pairs_10/```. Format: ```{'instance_id': {'question': Example question..., 'answer': Example answer}}```.
3. Extract terms. Extract important context terms and response terms. It can be very computational expensive to compute co-occurrence for every combination of question terms and answer terms. So we only focus on terms that appear in context and response candidates. An example for terms file is under ```terms/```. Format: ```instance_id \t contexts terms \t response terms```.

##### Output:
Each ```instance_id``` corresponds to a result dictionary. This dictionary is essentially a two-dimensional matrix. The row and column indice are terms and the values in the matrix is the PPMI for these two terms.  This dictionary has three keys. ```r_vocab``` and ```c_vocab``` map the row/column terms to their indices. We adopt this maping to save space. 'rcv_dict' is a dictionary that map the matrix index of ```(rIndex, cIndex)``` to its value. An example for output file is under
```inter_unique/```: outputs are in json, format: 
```
{
‘Instance_id’:{
	'rcv_dict': {key: ‘rIndex_cIndex’, value: m[rIndex][cIndex]}
	‘r_vocab': dict of vocabs for rows, [term2index]
	'c_vocab': dict of vocabs for cols, [term2index]
	}
}
```

##### Note
We use a distributed system for efficient computation. So the input and output files are in small partitions. All input files should be put into respective folders with the same ```run_id``` before running ```local_matrix.py```. For example, for partition #1, the ID file, the QA pairs file, and the terms file all have the same ```run_id``` (file name) of ```1```. The output files are named as the same ```run_id``` as input files. The output files can be merged for later use.


