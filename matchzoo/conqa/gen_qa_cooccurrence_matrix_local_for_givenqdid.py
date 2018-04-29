'''
Print out extracted QA occurrence term pairs from retrieved QA pairs
given a qid and a did

@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
@homepage: https://sites.google.com/site/lyangwww/
'''

import sys
import json
import numpy as np
from tqdm import tqdm
import base64

def read_dict(infile):
    word_dict = {}
    for line in open(infile):
        r = line.strip().split()
        word_dict[r[1]] = r[0]
    return word_dict

def init_copurs_dict(infile, config):
    c_dict = {}
    text1_maxlen = config['inputs']['share']['text1_maxlen']
    text2_maxlen = config['inputs']['share']['text2_maxlen']
    for line in tqdm(open(infile)):
        r = line.strip().split('\t')
        word_2dlist = []
        utt_start = 2
        for j in range(utt_start, len(r)):
            word_index = r[j].strip().split()
            if r[0].startswith('Q'):  # context
                max_len = min(text1_maxlen, len(word_index))
            else:  # response
                max_len = min(text2_maxlen, len(word_index))
            word_index = word_index[:max_len]
            word_2dlist.append(word_index)
        c_dict[r[0]] = word_2dlist
    return c_dict

if __name__ == '__main__':
    # extract qa_cooccurrence_matrix for a given context/response pair with qid/did
    if len(sys.argv) < 5:
        print ('please input params: data_name word/term target_qid target_did')
        exit(1)
    data_name = sys.argv[1] # ms or udc
    word_or_term = sys.argv[2] # word for DMN-KD-Word; term for DMN-KD-Term
    target_qid = sys.argv[3] # the target qid that we are interested in
    target_did = sys.argv[4] # the target did that we are interested in

    basedir = '../../data/' + data_name + '/ModelInput/'
    cur_data_dir = basedir + 'dmn_model_input/'
    model_file = '../config/' + data_name + '/dmn_cnn.config'
    json_file_name = 'new_unique.json' if (word_or_term == 'term') else 'new_nounique.json'
    m_file = '/mnt/scratch/chenqu/local_output/' + data_name + '/res/' + json_file_name
    local_m = json.load(open(m_file))
    print 'number of instances in this json file: ', len(local_m), m_file

    with open(model_file, 'r') as f:
        config = json.load(f)
    # Extract qa co-occurrence matrix for each (context_qid, utt_index, candidate_response_id) triple
    # in train/valid/test data
    word_dict_file = cur_data_dir + 'word_dict.txt'
    corpus_preprocessd_file = cur_data_dir + 'corpus_preprocessed.txt'
    id2term_dict = read_dict(word_dict_file) # key word_id; value term
    #id2corpus_dict = init_copurs_dict(corpus_preprocessd_file, config) # key corpus_id; value a 2D list of utterance words id -> [[]]
    out_file = cur_data_dir + 'qa_co_term_pair_ppmi_' + target_qid + '_' + target_did + '.txt'
    qa_co_term_pair_ppmi_all_file = cur_data_dir + 'qa_co_term_pair_ppmi_all'
    case_study_file = cur_data_dir  + 'case_study_examples.txt'
    qid_in_cs_file = set()
    with open(case_study_file) as fin:
        for l in fin:
            qid_in_cs_file.add(l.split('\t')[0])

    print 'test qid_in_cs_file len: ', len(qid_in_cs_file)

    print 'out_file path: ', out_file
    fout = open(out_file, 'w')
    fout_qacomat_all = open(qa_co_term_pair_ppmi_all_file, 'w')
    for data_part in list(['test']): # 'train', 'valid' try to compute this for test firstly
        relation_file = cur_data_dir + 'relation_' + data_part + '.txt' # !Note that here we need to read non-fd version of relation files to be consistant
        instanceID = 0
        with open(relation_file) as fin:
            print ('read file: ', relation_file)
            for l in tqdm(fin):
                instanceID += 1
                cur_instanceID = data_part + '_' + str(instanceID)
                if cur_instanceID not in local_m:
                    #print 'test: can not find instanceID in local_m: ', cur_instanceID, l.strip()
                    continue # if the local matrix of this instance is all 0, skip it
                else:
                    #print 'test: !!! find instanceID in local_m: ', cur_instanceID, l.strip()
                    if l.strip().split()[1] in qid_in_cs_file: # only consider qid in case study file
                        fout_qacomat_all.write(l)
                to = l.strip().split()  # label qid did
                if to[1] != target_qid or to[2] != target_did: # only look at information for this Q-D pair
                    continue
                m = local_m[cur_instanceID]
                term2col_dict = m['c_vocab']
                term2row_dict = m['r_vocab']
                col2term_dict = dict(zip(term2col_dict.values(), term2col_dict.keys()))
                row2term_dict = dict(zip(term2row_dict.values(), term2row_dict.keys()))
                #qws = id2corpus_dict[to[1]]
                #dws = id2corpus_dict[to[2]]
                if len(m) > 0:
                    # print out all QA term pairs and the corresponding PPMI
                    for rIndex_cIndex in m['rcv_dict']:
                        row = rIndex_cIndex.split('_')[0]
                        col = rIndex_cIndex.split('_')[1]
                        out_l = row2term_dict[row] + '\t' + col2term_dict[col] + '\t' + str(m['rcv_dict'][rIndex_cIndex])
                        print out_l
                        fout.write(out_l + '\n')
    fout.close()
    fout_qacomat_all.close()



















