'''
Generate the file to store the terms in dialog context and response
InstanceID \t dialogContextTerms(seperatedBySpace) \t responseTerms(seperatedBySpace)
Generate two files for MS/UDC respectively
@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
@homepage: https://sites.google.com/site/lyangwww/
'''

import sys
from tqdm import tqdm

def read_dict(infile):
    word_dict = {}
    for line in open(infile):
        r = line.strip().split()
        word_dict[r[1]] = r[0]
    return word_dict

def read_corpus(corpus_preprocessd_file, id2term_dict):
    id2corpus_terms = {}
    for line in tqdm(open(corpus_preprocessd_file)):
        term_set = set()
        r = line.strip().split('\t')
        id = r[0]
        for i in range(2, len(r)):
            utt = r[i]
            for w in utt.split():
                term_set.add(id2term_dict[w])
        id2corpus_terms[id] = ' '.join(list(term_set))
    return id2corpus_terms


if __name__ == '__main__':
    # extract qa_cooccurrence_matrix from the global matrix computed by Chen Qu
    if len(sys.argv) < 2:
        print ('please input params: data_name')
        exit(1)
    data_name = sys.argv[1] # ms or udc

    basedir = '../../data/' + data_name + '/ModelInput/'
    cur_data_dir = basedir + 'dmn_model_input/'
    word_dict_file = cur_data_dir + 'word_dict.txt'
    corpus_preprocessd_file = cur_data_dir + 'corpus_preprocessed.txt'
    id2term_dict = read_dict(word_dict_file) # key word_id; value term
    id2corpus_terms = read_corpus(corpus_preprocessd_file, id2term_dict)

    out_file = cur_data_dir + 'instanceID_contextTerms_respTerms.txt'
    fout = open(out_file, 'w')
    for data_part in list(['train', 'valid', 'test']):
        relation_file = cur_data_dir + 'relation_' + data_part + '.txt' # here we need to read non-fd version of relation files to be consistant
        instanceID = 1
        with open(relation_file) as fin:
            print ('read file: ', relation_file)
            for l in tqdm(fin):
                cur_instanceID = data_part + '_' + str(instanceID)
                to = l.strip().split()  # label qid did
                context_ts = id2corpus_terms[to[1]]
                resp_ts = id2corpus_terms[to[2]]
                fout.write(str(cur_instanceID) + '\t' + context_ts + '\t' +  resp_ts + '\n')
                instanceID += 1
    fout.close()













