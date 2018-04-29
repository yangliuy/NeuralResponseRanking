'''
Transfer the format of conversational QA data to the input data format of MatchZoo
We have multiple conversational data sets including Ubuntu, Douban, MS dialog and Alibaba Xiaomi, etc.
The input data format is 'label \t context \t response'

1. Note that the ids in corpus_train.txt/corpus_valid.txt/corpus_test.txt could be duplicated (e.g. T112638)
Thus we can't simply merge three corpus files into a single corpus file
How to fix this bug: generate unique ids for all text in train.txt, valid.txt, test.txt
2. The query ids can't be duplicated. For the same query id, the document ids can't be duplicated.
Note that if we make queries with unique id (fixed 10 candidates for a single query), then it is
possible that multiple queries have different query ids, but with the same text (in rare cases)
3. For the same query, the candidate responses can't be duplicated. Thus we filter those queries containing duplicated
document ids. Such queries should be filtered and can't be evaluated

updated on 04022018 for the data preprocessing of MSDialog_V2 data
@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
@homepage: https://sites.google.com/site/lyangwww/
'''

# /bin/python2.7
import os
import sys
sys.path.append('../../matchzoo/inputs/')
sys.path.append('../../matchzoo/utils/')

from preparation import Preparation
from preprocess import Preprocess, NgramUtil

def read_dict(infile):
    word_dict = {}
    for line in open(infile):
        r = line.strip().split()
        word_dict[r[1]] = r[0]
    return word_dict

def transfer_to_matchzoo_input_format(sample_file):
    '''
    :param sample_file: input file in original format where the context may be seperated by \t
    :return: transferred_file: transferred file in the input data format of matchzoo
    If the context is seperated by \t, this function will merged them by space
    If the context is already merged, this function will join a space with this context
    The input data format of matchzoo is 'label \t context \t response'
    '''
    with open(sample_file, 'r') as f_in, open(sample_file + '.mz', 'w') as f_out:
       for line in f_in:
           tokens = line.split('\t')
           t_len = len(tokens)
           label = tokens[0]
           context = ' '.join(tokens[1:t_len-1])
           response = tokens[t_len-1]
           l_out = label + '\t' + context + '\t' + response
           f_out.write(l_out)
           #print 'test line len: ', len(l_out.split('\t'))
    return sample_file + '.mz'

if __name__ == '__main__':
    basedir = '../../data/ms_v2/ModelInput/'
    # basedir = '../../data/MicrosoftCSQA/ModelInput/'
    # basedir = '../../data/DoubanConversaionCorpusACL17YuWu/ModelInput/'
    # transform context/response pairs into corpus file and relation file
    # the input files are train.txt/valid.txt/test.txt
    # the format of each line is 'label context response'
    prepare = Preparation()
    for data_part in list(['train', 'valid', 'test']):
        print 'generate matchzoo data format for part: ', data_part
        sample_file = basedir + data_part + '.txt'
        sample_file_matchzoo_format = transfer_to_matchzoo_input_format(sample_file)
    # run with three files (train.txt.mz, valid.txt.mz, test.txt.mz) to generate unique ids
    # for q/d in train/valid/test data. Since we will merge these three corpus files into a single file later
    corpus, rels_train, rels_valid, rels_test = prepare.run_with_train_valid_test_corpus(
        basedir + 'train.txt.mz', basedir + 'valid.txt.mz', basedir + 'test.txt.mz')

    for data_part in list(['train', 'valid', 'test']):
        if data_part == 'train':
            rels = rels_train
        elif data_part == 'valid':
            rels = rels_valid
        else:
            rels = rels_test
        print 'total relations in ', data_part, len(rels)
        prepare.save_relation(basedir + 'relation_' + data_part + '.txt', rels)
        print 'filter queries with duplicated doc ids...'
        prepare.check_filter_query_with_dup_doc(basedir + 'relation_' + data_part + '.txt')
    print 'total corpus ', len(corpus)
    prepare.save_corpus(basedir + 'corpus.txt', corpus)
    print 'preparation finished ...'

    print 'begin preprocess...'
    # Prerpocess corpus file
    preprocessor = Preprocess(word_filter_config={'min_freq': 5})
    dids, docs = preprocessor.run(basedir + 'corpus.txt')
    preprocessor.save_word_dict(basedir + 'word_dict.txt')
    # preprocessor.save_words_df(basedir + 'word_df.txt')

    fout = open(basedir + 'corpus_preprocessed.txt','w')
    for inum,did in enumerate(dids):
        fout.write('%s\t%s\t%s\n' % (did, len(docs[inum]), ' '.join(map(str, docs[inum])))) # id text_len text_ids
    fout.close()
    print('preprocess finished ...')

    print('triletter processing begin ...')
    word_dict_input = basedir + 'word_dict.txt'
    triletter_dict_output = basedir + 'triletter_dict.txt'
    word_triletter_output = basedir + 'word_triletter_map.txt'
    word_dict = read_dict(word_dict_input)
    triletter_dict = {}
    word_triletter_map = {}
    for wid, word in word_dict.items():
        nword = '#' + word + '#'
        ngrams = NgramUtil.ngrams(list(nword), 3, '')
        word_triletter_map[wid] = []
        for tric in ngrams:
            if tric not in triletter_dict:
                triletter_dict[tric] = len(triletter_dict)
            word_triletter_map[wid].append(triletter_dict[tric])
    with open(triletter_dict_output, 'w') as f:
        for tri_id, tric in triletter_dict.items():
            print >> f, tri_id, tric
    with open(word_triletter_output, 'w') as f:
        for wid, tri_ids in word_triletter_map.items():
            print >> f, wid, ' '.join(map(str, tri_ids))
    print('triletter processing finished ...')

