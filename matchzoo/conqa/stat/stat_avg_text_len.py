'''
Compute the average length of text1 and text2
Set text1_maxlen and text2_maxlen as 2*avg_len
@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
'''

import numpy as np
from tqdm import tqdm

def load_corpus(corpus_preprocessed_file):
    id_to_text_dict = dict()
    print 'load id_to_text_dict ...'
    with open(corpus_preprocessed_file, 'r') as f_in:
        for l in tqdm(f_in):
            tokens = l.split('\t')
            id_to_text_dict[tokens[0]] = list(tokens[2].split(' '))
    return  id_to_text_dict

if __name__ == '__main__':
    basedir = '../../../data/ms_v2/ModelInput/'
    # basedir = '../../data/MicrosoftCSQA/ModelInput/'
    # basedir = '../../data/DoubanConversaionCorpusACL17YuWu/ModelInput/'
    # transform context/response pairs into corpus file and relation file
    # the input files are train.txt/valid.txt/test.txt
    # the format of each line is 'label context response'
    corpus_preprocessed_file = basedir + 'corpus_preprocessed.txt'
    id_to_text_dict = load_corpus(corpus_preprocessed_file)

    for data_part in list(['train', 'valid', 'test']):
        relation_file = basedir + 'relation_' + data_part + '.txt.fd'
        print 'compute avg text length for ', relation_file  # label qid did
        text1_len = []
        text2_len = []
        query_id_set = set()
        with open(relation_file) as f_in:
            for l in f_in:
                to = l.split(' ')
                #print 'to: ', to
                #le = len(id_to_text_dict[to[2]]) if to[2] in id_to_text_dict.keys() else 0
                text2_len.append(len(id_to_text_dict[to[2].strip()]))
                query_id_set.add(to[1])

        for qid in query_id_set:
            #le = len(id_to_text_dict[qid]) if qid in id_to_text_dict.keys() else 0
            text1_len.append(len(id_to_text_dict[qid]))

        print 'text1_avg_len, text2_avg_len'
        print np.average(np.array(text1_len)), np.average(np.array(text2_len))
        print np.median(np.array(text1_len)), np.median(np.array(text2_len))