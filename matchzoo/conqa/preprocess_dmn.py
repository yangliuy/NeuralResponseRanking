'''
The script for data preprocess of DMN model
The input data format is label \t context (utterances seperated by \t) \t response
The input query/X1 becomes a 2D matrix [window_utterance_num * max_utterance_len]
The input doc/X2 is still the same, which is 1D vector [max_response_len]
max_utterance_len and max_response_len are corresponding to max_tex1_len and max_text2_len
corpus.txt: id \t utt1 \t utt2 ...
corpus_preprocessed.txt: id \t utt_num \t word_index_utt1 \t word_index_utt2 ...
Match a text matrix (text1) with a text vector (text2)
@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
@homepage: https://sites.google.com/site/lyangwww/
'''

# /bin/python2.7
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

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print 'please input params: data_name (udc or ms_v2)'
        exit(1)
    data_name = sys.argv[1] # ms or udc or ms_v2

    basedir = '../../data/' + data_name + '/ModelInput/'
    cur_data_dir = basedir + 'dmn_model_input/'

    prepare = Preparation()

    if data_name == 'udc' or data_name == 'ms_v2':
        train_file = 'train.txt'
        valid_file = 'valid.txt'
        test_file = 'test.txt'
    else:
        raise ValueError('invalid data name!')

    corpus, rels_train, rels_valid, rels_test = prepare.run_with_train_valid_test_corpus_dmn(
        basedir + train_file, basedir + valid_file,
        basedir + test_file)
    for data_part in list(['train', 'valid', 'test']):
        if data_part == 'train':
            rels = rels_train
        elif data_part == 'valid':
            rels = rels_valid
        else:
            rels = rels_test
        print 'total relations in ', data_part, len(rels)
        prepare.save_relation(cur_data_dir + 'relation_' + data_part + '.txt', rels)
        print 'filter queries with duplicated doc ids...'
        prepare.check_filter_query_with_dup_doc(cur_data_dir + 'relation_' + data_part + '.txt')
    print 'total corpus ', len(corpus)
    prepare.save_corpus_dmn(cur_data_dir + 'corpus.txt', corpus, '\t')
    print 'preparation finished ...'

    print 'begin preprocess...'
    # Prerpocess corpus file
    preprocessor = Preprocess(word_filter_config={'min_freq': 5})
    dids, docs = preprocessor.run_2d(cur_data_dir + 'corpus.txt') # docs is [corpus_size, utterance_num, max_text1_len]
    preprocessor.save_word_dict(cur_data_dir + 'word_dict.txt')
    # preprocessor.save_words_df(basedir + 'word_df.txt')

    fout = open(cur_data_dir + 'corpus_preprocessed.txt','w')
    for inum,did in enumerate(dids):
        doc_txt = docs[inum] # 2d list
        doc_string = ''
        for utt in doc_txt:
            for w in utt:
                doc_string += str(w) + ' '
            doc_string += '\t'
        fout.write('%s\t%s\t%s\n' % (did, len(docs[inum]), doc_string )) # id text_len text_ids
    fout.close()
    print('preprocess finished ...')

