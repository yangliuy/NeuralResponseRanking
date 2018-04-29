'''
QA Connection Knowledge Distillation from Relevant QA Pairs for DMN-KD model (V4-2)
Global: Compute a global term co-occurrence matrix from the product related QA pairs.
 Then for each (u_t, r_k) pair, just select the corresponding row/col/sub-matrix as the
 external knowledge matrix to integrate into the model (Give a math definition on this
 . Refer to the trick of mask matrix / permutation matrix/one-hot, multi-hot matrix) / slicing
 tricks of pandas/numpy
Local: Which constructs a term-co-occurrence matrix for each (u_t,r_k) pair in advance. This setting
 could cost more time and storage

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
    # extract qa_cooccurrence_matrix from the global matrix computed by Chen Qu
    if len(sys.argv) < 3:
        print ('please input params: data_name word/term')
        exit(1)
    data_name = sys.argv[1] # ms or udc or ms_v2
    word_or_term = sys.argv[2] # word for DMN-KD-Word; term for DMN-KD-Term

    basedir = '../../data/' + data_name + '/ModelInput/'
    cur_data_dir = basedir + 'dmn_model_input/'
    model_file = '../config/' + data_name + '/dmn_cnn.config'
    json_file_name = 'new_unique.json' if (word_or_term == 'term') else 'new_nounique.json'
    data_name_qc = data_name if data_name != 'ms_v2' else 'ms'
    m_file = '/mnt/scratch/chenqu/local_output/' + data_name_qc + '/res/' + json_file_name
    local_m = json.load(open(m_file))
    print 'number of instances in this json file: ', len(local_m), m_file

    with open(model_file, 'r') as f:
        config = json.load(f)
    # Extract qa co-occurrence matrix for each (context_qid, utt_index, candidate_response_id) triple
    # in train/valid/test data
    word_dict_file = cur_data_dir + 'word_dict.txt'
    corpus_preprocessd_file = cur_data_dir + 'corpus_preprocessed.txt'
    id2term_dict = read_dict(word_dict_file) # key word_id; value term
    id2corpus_dict = init_copurs_dict(corpus_preprocessd_file, config) # key corpus_id; value a 2D list of utterance words id -> [[]]
    out_file = (cur_data_dir + 'qa_comat_local_term.txt') if (word_or_term == 'term') else (cur_data_dir + 'qa_comat_local_word.txt')
    print 'out_file path: ', out_file
    fout = open(out_file, 'w')
    for data_part in list(['train', 'valid', 'test']): # 'train', 'valid' try to compute this for test firstly
        relation_file = cur_data_dir + 'relation_' + data_part + '.txt' # !Note that here we need to read non-fd version of relation files to be consistant
        instanceID = 0
        with open(relation_file) as fin:
            print ('read file: ', relation_file)
            for l in tqdm(fin):
                instanceID += 1
                cur_instanceID = data_part + '_' + str(instanceID)
                # if instanceID > 100:
                #     break
                if cur_instanceID not in local_m:
                    #print 'test: can not find instanceID: ', cur_instanceID
                    continue # if the local matrix of this instance is all 0, skip it
                m = local_m[cur_instanceID]
                term2col_dict = m['c_vocab']
                term2row_dict = m['r_vocab']

                to = l.strip().split() # label qid did
                qws = id2corpus_dict[to[1]]
                dws = id2corpus_dict[to[2]]
                for j in range(len(qws)): # iter utterances in context
                    #np_sub_matrix = np.zeros((text1_maxlen, text2_maxlen), dtype=np.float32)
                    crv_list_c = [] # a 2D sparse matrix for QA co-occur matrix given (qid, uid, rid)
                    crv_list_r = []  # a 2D sparse matrix for QA co-occur matrix given (qid, uid, rid)
                    crv_list_v = []  # a 2D sparse matrix for QA co-occur matrix given (qid, uid, rid)
                    # >> rows = np.array([1, 2, 3])
                    # >> cols = np.array([2, 3, 4])
                    # >> values = np.array([5, 6, 7])
                    # >> a[rows, cols] = values
                    utt = qws[j]
                    if len(dws) > 0:
                        resp = dws[0]
                        for ii in range(len(utt)):
                            for jj in range(len(resp)):
                                utt_term = id2term_dict[utt[ii]]
                                resp_term = id2term_dict[resp[jj]]
                                if utt_term in term2row_dict and resp_term in term2col_dict:
                                    rcv_dict_key = str(term2row_dict[utt_term]) + '_' + str(term2col_dict[resp_term])
                                    if rcv_dict_key in m['rcv_dict']:
                                        v = m['rcv_dict'][rcv_dict_key]
                                        if v != 0. and v != 0: # only store one zero values
                                            crv_list_r.append(ii)
                                            crv_list_c.append(jj)
                                            crv_list_v.append(v)
                    k = to[1] + '_' + str(j) + '_' + to[2] # the key is qid_uid_rid
                    if len(crv_list_v) > 0: # only write matrix if there is at least one non-zero element
                        fout.write(k + '\t' + base64.b32encode(np.array([crv_list_r, crv_list_c, crv_list_v], dtype=np.float32).dumps()) + '\n')
                    # test
                #break
    fout.close()

    print ('extract local qa co-occurrence matrix done!')
    with open(out_file) as fin:
        l_index = 0
        for l in fin:
            l_index += 1
            to = l.strip().split('\t')
            m = np.loads(base64.b32decode(to[1]))
            print ('test key and m: ', to[0], m)
            if l_index > 1:
                break

















